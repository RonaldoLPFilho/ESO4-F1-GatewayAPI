package com.example.gatewayapi.adapters.outbound.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class TwilioSmsClient {

    private final WebClient webClient;
    private final String accountSid;
    private final String fromNumber;
    private final boolean enabled;

    public TwilioSmsClient(
            @Value("${twilio.accountSid:}") String accountSid,
            @Value("${twilio.authToken:}") String authToken,
            @Value("${twilio.fromNumber:}") String fromNumber,
            @Value("${twilio.enabled:false}") boolean enabledFlag,
            @Value("${twilio.timeoutMillis:15000}") long timeoutMillis
    ) {
        this.accountSid = safe(accountSid);
        String token = safe(authToken);
        this.fromNumber = safe(fromNumber);
        this.enabled = enabledFlag
                && !this.accountSid.isBlank()
                && !token.isBlank()
                && !this.fromNumber.isBlank();

        if (this.enabled) {
            HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofMillis(timeoutMillis))
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMillis)
                    .doOnConnected(conn -> conn
                            .addHandlerLast(new ReadTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS))
                    );

            this.webClient = WebClient.builder()
                    .baseUrl("https://api.twilio.com")
                    .defaultHeaders(headers -> headers.setBasicAuth(this.accountSid, token))
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
        } else {
            this.webClient = null;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public SmsSendResult send(String to, String body) {
        if (!enabled) {
            return new SmsSendResult(false, "Twilio não configurado", null);
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", normalizePhone(to));
        form.add("From", fromNumber);
        form.add("Body", body);

        try {
            JsonNode response = webClient.post()
                    .uri("/2010-04-01/Accounts/{accountSid}/Messages.json", accountSid)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block();

            String sid = response != null && response.hasNonNull("sid")
                    ? response.get("sid").asText()
                    : null;
            return new SmsSendResult(true, null, sid);
        } catch (WebClientResponseException ex) {
            return new SmsSendResult(false, extractTwilioError(ex), null);
        } catch (Exception ex) {
            return new SmsSendResult(false, ex.getMessage(), null);
        }
    }

    static String normalizePhone(String raw) {
        String trimmed = safe(raw);
        if (trimmed.isBlank()) {
            return trimmed;
        }

        String digits = trimmed.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("55") && digits.length() >= 12) {
            return "+" + digits;
        }
        if (digits.length() >= 10 && digits.length() <= 11) {
            return "+55" + digits;
        }
        return "+" + digits;
    }

    private static String extractTwilioError(WebClientResponseException ex) {
        try {
            JsonNode body = ex.getResponseBodyAs(JsonNode.class);
            if (body != null && body.hasNonNull("message")) {
                return body.get("message").asText();
            }
        } catch (Exception ignored) {
            // fallback abaixo
        }
        return ex.getStatusText() + " (" + ex.getStatusCode().value() + ")";
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    public record SmsSendResult(boolean success, String errorMessage, String sid) {}
}

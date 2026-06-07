package com.example.gatewayapi.adapters.outbound.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
public class OpenAiResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiResponsesClient.class);

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;

    @Autowired
    public OpenAiResponsesClient(
            WebClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${openai.baseUrl:https://api.openai.com/v1}") String baseUrl,
            @Value("${openai.model:gpt-5.2}") String model,
            @Value("${openai.timeoutMillis:30000}") long timeoutMillis,
            @Value("${openai.apiKey:}") String configuredApiKey
    ) {
        this(
                buildWebClient(builder, baseUrl, timeoutMillis),
                objectMapper,
                resolveApiKey(configuredApiKey),
                model
        );
    }

    OpenAiResponsesClient(WebClient webClient, ObjectMapper objectMapper, String apiKey, String model) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    public String model() {
        return model;
    }

    public Mono<String> generateMarkdownReport(String instructions, String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("[OpenAI] OPENAI_API_KEY nao configurada — abortando chamada");
            return Mono.error(new IllegalStateException("OPENAI_API_KEY nao configurada"));
        }

        log.info("[OpenAI] Enviando requisicao para geracao de relatorio | model={}", model);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("instructions", instructions);
        payload.put("input", List.of(
                Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of(
                                        "type", "input_text",
                                        "text", prompt
                                )
                        )
                )
        ));

        return webClient.post()
                .uri("/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .bodyValue(payload)
                .retrieve()
                .onStatus(status -> !status.is2xxSuccessful(), clientResponse ->
                        clientResponse.bodyToMono(String.class).defaultIfEmpty("").map(body -> {
                            log.error("[OpenAI] Erro na resposta | status={} body={}", clientResponse.statusCode(), body);
                            return new WebClientResponseException(
                                    clientResponse.statusCode().value(),
                                    clientResponse.statusCode().toString(),
                                    null, body.getBytes(), null
                            );
                        })
                )
                .bodyToMono(JsonNode.class)
                .doOnNext(body -> log.info("[OpenAI] Resposta recebida com sucesso | model={}", model))
                .map(this::extractOutputText);
    }

    private String extractOutputText(JsonNode root) {
        JsonNode topLevel = root.get("output_text");
        if (topLevel != null && topLevel.isTextual()) {
            return topLevel.asText().trim();
        }

        StringBuilder sb = new StringBuilder();
        JsonNode output = root.get("output");
        if (output != null && output.isArray()) {
            for (JsonNode item : output) {
                JsonNode content = item.get("content");
                if (content == null || !content.isArray()) {
                    continue;
                }
                for (JsonNode part : content) {
                    if ("output_text".equals(part.path("type").asText()) && part.hasNonNull("text")) {
                        if (!sb.isEmpty()) {
                            sb.append('\n');
                        }
                        sb.append(part.path("text").asText());
                    }
                }
            }
        }

        if (!sb.isEmpty()) {
            return sb.toString().trim();
        }

        return objectMapper.valueToTree(root).toPrettyString();
    }

    private static WebClient buildWebClient(WebClient.Builder builder, String baseUrl, long timeoutMillis) {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMillis))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMillis)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS)));

        return builder
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private static String resolveApiKey(String configuredApiKey) {
        if (configuredApiKey != null && !configuredApiKey.isBlank()) {
            return configuredApiKey.trim();
        }
        String env = System.getenv("OPENAI_API_KEY");
        return env == null ? "" : env.trim();
    }
}

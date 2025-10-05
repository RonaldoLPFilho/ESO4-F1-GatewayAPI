package com.example.gatewayapi.adapters.outbound.client;

import com.example.gatewayapi.adapters.outbound.dto.VisionMetricsDTO;
import com.example.gatewayapi.adapters.outbound.dto.VisionPredictResponseDTO;
import com.example.gatewayapi.domain.model.ClassificationResult;
import com.example.gatewayapi.domain.port.VisionModelPort;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class VisionClient implements VisionModelPort {
    private final WebClient webClient;

    public VisionClient(@Value("${vision.baseUrl}") String baseUrl,
                        @Value("${vision.timeoutMillis}") long timeoutMillis) {

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofMillis(timeoutMillis))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) timeoutMillis)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(timeoutMillis, TimeUnit.MILLISECONDS))
                );

        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public Mono<ClassificationResult> predict(byte[] imageBytes, String filename) {
        var filePart = new org.springframework.core.io.ByteArrayResource(imageBytes) {
            @Override public String getFilename() { return filename != null ? filename : "image.jpg"; }
        };

        return webClient.post()
                .uri("/predict")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData("image", filePart))
                .retrieve()
                .bodyToMono(VisionPredictResponseDTO.class)
                .map(v -> new ClassificationResult(
                        v.label(),
                        v.confidence(),
                        v.food(),
                        v.model_version(),
                        v.processing_ms()
                ));
    }

    public Mono<VisionMetricsDTO> metrics(){
        return webClient.get().uri("/metrics")
                .retrieve()
                .bodyToMono(VisionMetricsDTO.class);
    }
}

package com.example.gatewayapi.adapters.outbound.client;

import com.example.gatewayapi.adapters.outbound.dto.VisionPredictResponseDTO;
import com.example.gatewayapi.domain.model.ClassificationResult;
import com.example.gatewayapi.domain.port.VisionModelPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class VisionClient implements VisionModelPort {
    private final WebClient webClient;

    public VisionClient(@Value("${vision.baseUrl}") String baseUrl,
                        @Value("${vision.timeoutMillis}") long timeoutMillis) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
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
                        v.id(),
                        v.confidence(),
                        v.model_version(),
                        v.processing_ms()
                ));
    }
}

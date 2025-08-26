package com.example.gatewayapi.domain.port;

import com.example.gatewayapi.domain.model.ClassificationResult;
import reactor.core.publisher.Mono;

public interface VisionModelPort {
    Mono<ClassificationResult> predict(byte[] imageBytes, String fileName);
}

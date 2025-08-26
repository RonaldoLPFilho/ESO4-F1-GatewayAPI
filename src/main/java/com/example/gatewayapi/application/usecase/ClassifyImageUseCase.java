package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.domain.model.ClassificationResult;
import com.example.gatewayapi.domain.port.VisionModelPort;
import reactor.core.publisher.Mono;

public class ClassifyImageUseCase {
    private final VisionModelPort visionModelPort;

    public ClassifyImageUseCase(VisionModelPort visionModelPort) {
        this.visionModelPort = visionModelPort;
    }

    public Mono<ClassificationResult> execute(byte[] imageBytes, String fileName) {
        return visionModelPort.predict(imageBytes, fileName);
    }
}

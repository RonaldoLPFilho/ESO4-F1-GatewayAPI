package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.domain.model.ClassificationRecord;
import com.example.gatewayapi.domain.port.ClassificationResultRepositoryPort;
import reactor.core.publisher.Mono;

public class SaveClassificationResultUseCase {
    private final ClassificationResultRepositoryPort repo;

    public SaveClassificationResultUseCase(ClassificationResultRepositoryPort repo) {
        this.repo = repo;
    }

    public Mono<ClassificationRecord> execute(ClassificationRecord record){
        return repo.save(record);
    }
}

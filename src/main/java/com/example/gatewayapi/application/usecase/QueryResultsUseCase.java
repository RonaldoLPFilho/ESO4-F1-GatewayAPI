package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.domain.model.ClassificationRecord;
import com.example.gatewayapi.domain.port.ClassificationResultRepositoryPort;
import reactor.core.publisher.Flux;

import java.time.Instant;

public class QueryResultsUseCase {
    private final ClassificationResultRepositoryPort repo;

    public QueryResultsUseCase(ClassificationResultRepositoryPort repo) {
        this.repo = repo;
    }

    public Flux<ClassificationRecord> all(){
        return repo.findAll();
    }

    public Flux<ClassificationRecord> between(Instant start, Instant end){
        return repo.findBetween(start, end);
    }
}

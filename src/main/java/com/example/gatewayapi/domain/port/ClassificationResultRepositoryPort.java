package com.example.gatewayapi.domain.port;

import com.example.gatewayapi.domain.model.ClassificationRecord;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface ClassificationResultRepositoryPort {
    Mono<ClassificationRecord> save(ClassificationRecord record);
    Flux<ClassificationRecord> findAll();
    Flux<ClassificationRecord> findBetween(Instant start, Instant end);
}

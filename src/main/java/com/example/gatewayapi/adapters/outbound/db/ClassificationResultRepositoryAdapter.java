package com.example.gatewayapi.adapters.outbound.db;

import com.example.gatewayapi.domain.model.ClassificationRecord;
import com.example.gatewayapi.domain.port.ClassificationResultRepositoryPort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@Component
public class ClassificationResultRepositoryAdapter implements ClassificationResultRepositoryPort {

    private final JpaClassificationResultRepository jpa;

    public ClassificationResultRepositoryAdapter(JpaClassificationResultRepository jpa) {
        this.jpa = jpa;
    }

    private static ClassificationRecord toDomain(ClassificationResultEntity e){
        return new ClassificationRecord(
                e.getId(), e.getTimestamp(), e.getSource(), e.getImageName(),
                e.getPredictedLabel(), e.getFood() ,e.getConfidence(), e.getModelVersion(), e.getRequestId()
        );
    }

    private static ClassificationResultEntity toEntity(ClassificationRecord r){
        return new ClassificationResultEntity(
                null, r.timestamp(), r.source(), r.imageName(),
                r.predictedLabel(), r.food(),r.confidence(), r.modelVersion(), r.requestId()
        );
    }

    @Override
    public Mono<ClassificationRecord> save(ClassificationRecord record) {
        return Mono.fromCallable(() -> jpa.save(toEntity(record)))
                .subscribeOn(Schedulers.boundedElastic())
                .map(ClassificationResultRepositoryAdapter::toDomain);
    }

    @Override
    public Flux<ClassificationRecord> findAll() {
        return Mono.fromCallable(jpa::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(list -> Flux.fromIterable(list.stream()
                        .sorted((a,b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                        .map(ClassificationResultRepositoryAdapter::toDomain)
                        .toList()
                ));
    }

    @Override
    public Flux<ClassificationRecord> findBetween(Instant start, Instant end) {
        return Mono.fromCallable(() -> jpa.findByTimestampBetweenOrderByTimestampDesc(start, end))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(list -> Flux.fromIterable(list.stream()
                        .map(ClassificationResultRepositoryAdapter::toDomain)
                        .toList()
                ));
    }
}

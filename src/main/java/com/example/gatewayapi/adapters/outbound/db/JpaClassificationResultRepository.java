package com.example.gatewayapi.adapters.outbound.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface JpaClassificationResultRepository  extends JpaRepository<ClassificationResultEntity, UUID> {
    List<ClassificationResultEntity> findByTimestampBetweenOrderByTimestampDesc(Instant start, Instant end);
}

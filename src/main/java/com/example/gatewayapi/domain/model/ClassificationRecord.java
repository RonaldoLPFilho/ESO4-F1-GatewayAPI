package com.example.gatewayapi.domain.model;

import java.time.Instant;
import java.util.UUID;

public record ClassificationRecord(
        UUID id,
        Instant timestamp,
        String source,
        String imageName,
        String predictedLabel, // Saudavel | doente
        String food,
        Double confidence,
        String modelVersion,
        String requestId
) {}

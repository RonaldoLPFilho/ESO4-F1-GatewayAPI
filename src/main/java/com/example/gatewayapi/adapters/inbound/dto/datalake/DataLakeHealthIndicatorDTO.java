package com.example.gatewayapi.adapters.inbound.dto.datalake;

public record DataLakeHealthIndicatorDTO(
        String date,
        String crop,
        long totalImages,
        long healthyCount,
        long sickCount,
        double sickRate,
        double avgConfidence
) {}

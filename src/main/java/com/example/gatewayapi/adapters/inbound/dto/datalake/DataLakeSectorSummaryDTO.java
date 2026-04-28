package com.example.gatewayapi.adapters.inbound.dto.datalake;

public record DataLakeSectorSummaryDTO(
        String date,
        String sector,
        String cropFocus,
        String farmId,
        long totalImages,
        long sickCount,
        long healthyCount,
        double sickRate,
        double avgTemperatureC,
        double avgHumidityPct,
        double avgSoilMoisture,
        double weatherRainProbability,
        long alertsTriggered,
        double riskScore,
        String riskLevel
) {}

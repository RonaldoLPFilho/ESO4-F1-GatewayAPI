package com.example.gatewayapi.adapters.outbound.dto;

public record VisionMetricsDTO (
        String model_version,
        Double accuracy,
        Double precision,
        Double recall,
        Double f1,
        String tested_at
){}

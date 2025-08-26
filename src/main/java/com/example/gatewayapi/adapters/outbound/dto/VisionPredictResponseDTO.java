package com.example.gatewayapi.adapters.outbound.dto;

public record VisionPredictResponseDTO(
        String id,
        Double confidence,
        String model_version,
        Integer processing_ms
) {}

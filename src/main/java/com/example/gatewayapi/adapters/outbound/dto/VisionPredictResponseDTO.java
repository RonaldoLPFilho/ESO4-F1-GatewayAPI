package com.example.gatewayapi.adapters.outbound.dto;

public record VisionPredictResponseDTO(
        String label,
        Double confidence,
        String model_version,
        Integer processing_ms
) {}

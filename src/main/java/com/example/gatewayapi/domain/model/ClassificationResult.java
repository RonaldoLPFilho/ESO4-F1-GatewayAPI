package com.example.gatewayapi.domain.model;

import lombok.Data;

public record ClassificationResult(
        String label,
        Double confidence,
        String food,
        String modelVersion,
        Integer processingMs
) {}
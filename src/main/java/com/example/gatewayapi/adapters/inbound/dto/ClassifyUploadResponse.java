package com.example.gatewayapi.adapters.inbound.dto;

public record ClassifyUploadResponse(
        String id,
        String imageName,
        String predictedLabel,
        Double confidence,
        String modelVersion,
        String timestamp,
        String source
) {}

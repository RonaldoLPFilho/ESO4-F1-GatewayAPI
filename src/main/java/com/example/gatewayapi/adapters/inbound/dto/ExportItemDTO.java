package com.example.gatewayapi.adapters.inbound.dto;

public record ExportItemDTO (
        String timeStamp,
        String imageName,
        String source,
        String predictedLabel,
        Double confidence,
        String modelVersion,
        String requestId
){}


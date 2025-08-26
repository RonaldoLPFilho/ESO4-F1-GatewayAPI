package com.example.gatewayapi.adapters.inbound.dto;

public record ClassifyWebcamResponse (
        String predictedLabel,
        Double confidence,
        String modelVersion,
        String timestamp,
        String source
){ }

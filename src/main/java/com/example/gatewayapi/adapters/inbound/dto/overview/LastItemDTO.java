package com.example.gatewayapi.adapters.inbound.dto.overview;

import com.example.gatewayapi.adapters.outbound.db.ClassificationResultEntity;

public record LastItemDTO(String timestamp, String imageName, String food, String predictedLabel, double confidence) {
    public static LastItemDTO from(ClassificationResultEntity r) {
        return new LastItemDTO(
                r.getTimestamp().toString(),
                r.getImageName(),
                r.getFood(),
                r.getPredictedLabel(),
                r.getConfidence() != null ? r.getConfidence() : 0.0
        );
    }
}
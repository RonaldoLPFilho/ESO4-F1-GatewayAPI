package com.example.gatewayapi.adapters.inbound.dto.overview;

public record FoodBucketDTO(String food, long healthy, long sick, double rate) {
    public long total() { return healthy + sick; }
}
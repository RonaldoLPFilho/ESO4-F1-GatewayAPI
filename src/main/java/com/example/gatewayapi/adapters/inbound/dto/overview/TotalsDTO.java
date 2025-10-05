package com.example.gatewayapi.adapters.inbound.dto.overview;

public record TotalsDTO(long total, long healthy, long sick, long unknown, double healthRate) {}

package com.example.gatewayapi.adapters.inbound.dto.alerts;

public record AlertContactDTO(
        String id,
        String name,
        String email,
        String phone
) {}

package com.example.gatewayapi.adapters.inbound.dto.alerts;

public record AlertEventDTO(
        String id,
        String triggeredAt,
        Integer sickCount,
        Integer windowMinutes,
        Integer thresholdSick,
        AlertChannelDTO channel,
        AlertStatusDTO status,
        String errorMessage
) {}

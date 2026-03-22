package com.example.gatewayapi.adapters.inbound.dto.alerts;

public record AlertConfigDTO(
        String id,
        Integer windowMinutes,
        Integer thresholdSick,
        Integer cooldownMinutes,
        Boolean active,
        String lastTriggeredAt
) {}

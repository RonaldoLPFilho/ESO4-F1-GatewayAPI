package com.example.gatewayapi.adapters.inbound.dto.datalake;

import java.util.Map;

public record DataLakeStatusResponse(
        String root,
        Map<String, Long> fileCounts,
        String lastRebuildAt
) {}

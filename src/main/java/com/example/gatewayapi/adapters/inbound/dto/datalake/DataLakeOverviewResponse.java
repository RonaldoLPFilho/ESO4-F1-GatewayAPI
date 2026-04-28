package com.example.gatewayapi.adapters.inbound.dto.datalake;

import java.util.List;
import java.util.Map;

public record DataLakeOverviewResponse(
        String root,
        String lastRebuildAt,
        Map<String, Long> fileCounts,
        List<Map<String, Object>> healthIndicators,
        List<Map<String, Object>> sectorRiskSummary,
        List<Map<String, Object>> aiContexts
) {
}

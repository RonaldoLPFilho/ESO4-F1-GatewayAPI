package com.example.gatewayapi.adapters.inbound.dto.datalake;

import java.util.List;
import java.util.Map;

public record DataLakeSummaryResponse(
        String root,
        String latestDate,
        String lastRebuildAt,
        Map<String, Long> fileCounts,
        List<String> availableDates,
        List<String> availableSectors,
        List<DataLakeHealthIndicatorDTO> healthIndicators,
        List<DataLakeSectorSummaryDTO> sectorSummaries,
        Map<String, Object> latestContext
) {}

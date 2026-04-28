package com.example.gatewayapi.adapters.inbound.dto.datalake;

public record DataLakeAiReportResponse(
        String status,
        String model,
        String generatedAt,
        String savedPath,
        String reportMarkdown,
        DataLakeSummaryResponse summary
) {}

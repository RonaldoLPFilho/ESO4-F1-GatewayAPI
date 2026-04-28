package com.example.gatewayapi.adapters.inbound.dto.datalake;

public record DataLakeAiReportRequest(
        String date,
        String sector,
        String crop,
        String tone
) {}

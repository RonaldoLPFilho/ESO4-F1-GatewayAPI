package com.example.gatewayapi.adapters.inbound.dto.datalake;

public record DataLakeIngestResponse(
        String domain,
        String path,
        String status
) {
}

package com.example.gatewayapi.adapters.inbound.dto.datalake;

import java.util.Map;

public record DataLakeRebuildResponse(
        String root,
        String rebuiltAt,
        int bronzeRecordsExported,
        int silverFilesWritten,
        int goldFilesWritten,
        Map<String, Object> summary
) {}

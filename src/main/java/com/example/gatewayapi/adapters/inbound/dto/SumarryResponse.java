package com.example.gatewayapi.adapters.inbound.dto;

import java.util.List;
import java.util.Map;

public record SumarryResponse(
        Map<String, Long> countsByLabel,
        Double avgConfidence,
        VisionMetrics metrics,
        List<ExportItemDTO> last
) {
    public record VisionMetrics(
            String modelVersion,
            Double accuracy,
            Double precision,
            Double recall,
            Double f1
    ){}
}

package com.example.gatewayapi.adapters.inbound.dto.overview;

import java.util.List;

public record DashboardOverviewResponse(
        PeriodDTO period,
        TotalsDTO totals,
        TrendsDTO trends,
        List<FoodBucketDTO> byFood,
        List<TimePointDTO> timeSeries,
        List<LastItemDTO> last
) {}
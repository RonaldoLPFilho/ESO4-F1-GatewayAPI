package com.example.gatewayapi.adapters.inbound.web;


import com.example.gatewayapi.adapters.inbound.dto.overview.DashboardOverviewResponse;
import com.example.gatewayapi.application.usecase.GetDashboardOverviewUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.*;
import java.util.Optional;

@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final GetDashboardOverviewUseCase useCase;

    public DashboardController(GetDashboardOverviewUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/overview")
    public Mono<ResponseEntity<DashboardOverviewResponse>> overview(
            @RequestParam Optional<String> range,            // today|week|month|year
            @RequestParam Optional<String> from,             // ISO-8601
            @RequestParam Optional<String> to,               // ISO-8601
            @RequestParam Optional<String> tz                // ex: America/Sao_Paulo
    ) {
        ZoneId zone = ZoneId.of(tz.orElse("America/Sao_Paulo"));
        Instant[] window = resolveWindow(range, from, to, zone);
        return useCase.execute(window[0], window[1], zone)
                .map(ResponseEntity::ok);
    }

    private Instant[] resolveWindow(Optional<String> range, Optional<String> from, Optional<String> to, ZoneId zone) {
        if (from.isPresent() && to.isPresent()) {
            return new Instant[]{Instant.parse(from.get()), Instant.parse(to.get())};
        }
        LocalDate today = LocalDate.now(zone);
        String r = range.orElse("today").toLowerCase();
        LocalDate start;
        LocalDate endExclusive;

        switch (r) {
            case "week" -> {
                DayOfWeek dow = today.getDayOfWeek();
                start = today.minusDays(dow.getValue() - 1L); // segunda
                endExclusive = start.plusDays(7);
            }
            case "month" -> {
                start = today.withDayOfMonth(1);
                endExclusive = start.plusMonths(1);
            }
            case "year" -> {
                start = today.withDayOfYear(1);
                endExclusive = start.plusYears(1);
            }
//            case "today" -> {
//                start = today;
//                endExclusive = today.plusDays(1);
//            }
            default -> {
                start = today;
                endExclusive = today.plusDays(1);
            }
        }
        ZonedDateTime zFrom = start.atStartOfDay(zone);
        ZonedDateTime zTo = endExclusive.atStartOfDay(zone);
        return new Instant[]{zFrom.toInstant(), zTo.toInstant()};
    }
}

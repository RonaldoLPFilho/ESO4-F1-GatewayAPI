package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.adapters.inbound.dto.overview.*;
import com.example.gatewayapi.adapters.outbound.db.ClassificationResultEntity;
import com.example.gatewayapi.adapters.outbound.db.JpaClassificationResultRepository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.*;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GetDashboardOverviewUseCase {

    private final JpaClassificationResultRepository repo;

    public GetDashboardOverviewUseCase(JpaClassificationResultRepository repo) {
        this.repo = repo;
    }

    public Mono<DashboardOverviewResponse> execute(Instant from, Instant to, ZoneId zoneId) {
        return Mono.fromCallable(() -> compute(from, to, zoneId))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DashboardOverviewResponse compute(Instant from, Instant to, ZoneId zoneId) {
        List<ClassificationResultEntity> items = repo.findByTimestampBetween(from, to);

        long total = items.size();
        long healthy = items.stream().filter(this::isHealthy).count();
        long sick = items.stream().filter(this::isSick).count();
        long unknown = total - healthy - sick;
        double healthRate = total == 0 ? 0.0 : (double) healthy / total;


        // by food
        Map<String, List<ClassificationResultEntity>> byFood = items.stream()
                .collect(Collectors.groupingBy(r -> Optional.ofNullable(r.getFood()).orElse("desconhecido")));
        List<FoodBucketDTO> foodBuckets = byFood.entrySet().stream().map(e -> {
                    List<ClassificationResultEntity> list = e.getValue();
                    long h = list.stream().filter(this::isHealthy).count();
                    long s = list.stream().filter(this::isSick).count();
                    double rate = (h + s) == 0 ? 0.0 : (double) h / (h + s);
                    return new FoodBucketDTO(e.getKey(), h, s, rate);
                }).sorted(Comparator.comparingLong(FoodBucketDTO::total).reversed())
                .toList();

        // time series por dia no TZ
        Map<LocalDate, List<ClassificationResultEntity>> byDay = items.stream()
                .collect(Collectors.groupingBy(r ->
                        LocalDateTime.ofInstant(r.getTimestamp(), zoneId).toLocalDate()
                ));
        List<TimePointDTO> series = byDay.entrySet().stream().map(e -> {
            var list = e.getValue();
            long h = list.stream().filter(this::isHealthy).count();
            long s = list.stream().filter(this::isSick).count();
            return new TimePointDTO(e.getKey().toString(), h, s, list.size());
        }).sorted(Comparator.comparing(TimePointDTO::date)).toList();

        // últimos 10
        List<ClassificationResultEntity> last10 = repo.findTop10ByTimestampBetweenOrderByTimestampDesc(from, to);
        List<LastItemDTO> last = last10.stream().map(LastItemDTO::from).toList();

        Duration span = Duration.between(from, to);
        Instant prevFrom = from.minus(span);
        Instant prevTo   = from;
        List<ClassificationResultEntity> prev = repo.findByTimestampBetween(prevFrom, prevTo);

        long prevTotal = prev.size();
        long prevHealthy = prev.stream().filter(this::isHealthy).count();
        long prevSick    = prev.stream().filter(this::isSick).count();

        double healthyDelta = rateDelta(prevTotal == 0 ? 0.0 : (double) prevHealthy / prevTotal, healthRate);
        double sickDelta    = rateDelta(prevTotal == 0 ? 0.0 : (double) prevSick / prevTotal, total == 0 ? 0.0 : (double) sick / total);

        return new DashboardOverviewResponse(
                new PeriodDTO(from.toString(), to.toString()),
                new TotalsDTO(total, healthy, sick, unknown, healthRate),
                new TrendsDTO(healthyDelta, sickDelta),
                foodBuckets,
                series,
                last
        );
    }

    private boolean isHealthy(ClassificationResultEntity r) {
        return r.getPredictedLabel() != null && r.getPredictedLabel().equalsIgnoreCase("saudavel");
    }
    private boolean isSick(ClassificationResultEntity r) {
        return r.getPredictedLabel() != null && r.getPredictedLabel().equalsIgnoreCase("doente");
    }
    private double rateDelta(double prev, double curr) { return curr - prev; }
}

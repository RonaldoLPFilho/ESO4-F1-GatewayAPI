package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertEventDTO;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeIngestResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeRebuildResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeStatusResponse;
import com.example.gatewayapi.domain.model.ClassificationRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class DataLakeFlowUseCase {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ISO_LOCAL_DATE;

    private final ObjectMapper objectMapper;
    private final Path dataLakeRoot;
    private final ZoneId zoneId;

    public DataLakeFlowUseCase(
            ObjectMapper objectMapper,
            @Value("${datalake.root:../datalake}") String dataLakeRoot,
            @Value("${datalake.zoneId:America/Sao_Paulo}") String zoneId
    ) {
        this.objectMapper = objectMapper;
        this.dataLakeRoot = Path.of(dataLakeRoot).toAbsolutePath().normalize();
        this.zoneId = ZoneId.of(zoneId);
    }

    public Mono<Void> exportClassification(ClassificationRecord record) {
        return Mono.fromRunnable(() -> safeRun(() -> {
                    Instant timestamp = record.timestamp() != null ? record.timestamp() : Instant.now();
                    Path target = bronzePath("classifications", timestamp, fileName("classification", record.requestId(), ".json"));
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("request_id", record.requestId());
                    payload.put("timestamp", timestamp.toString());
                    payload.put("source", record.source());
                    payload.put("farm_id", "fazenda-01");
                    payload.put("sector", sectorFor(record));
                    payload.put("lot_id", lotFor(record));
                    payload.put("crop", cropFor(record));
                    payload.put("image_name", record.imageName());
                    payload.put("predicted_label", record.predictedLabel());
                    payload.put("confidence", record.confidence());
                    payload.put("model_version", record.modelVersion());
                    writeJson(target, payload);
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Void> exportAlertEvents(List<AlertEventDTO> events, String farmId, String sector) {
        return Mono.fromRunnable(() -> safeRun(() -> {
                    for (AlertEventDTO event : events) {
                        Instant triggeredAt = event.triggeredAt() != null ? Instant.parse(event.triggeredAt()) : Instant.now();
                        Path target = bronzePath("alerts", triggeredAt, fileName("alert", event.id(), ".json"));
                        Map<String, Object> payload = new LinkedHashMap<>();
                        payload.put("id", event.id());
                        payload.put("triggered_at", triggeredAt.toString());
                        payload.put("farm_id", farmId);
                        payload.put("sector", sector);
                        payload.put("sick_count", event.sickCount());
                        payload.put("window_minutes", event.windowMinutes());
                        payload.put("threshold_sick", event.thresholdSick());
                        payload.put("channel", event.channel() != null ? event.channel().name() : null);
                        payload.put("status", event.status() != null ? event.status().name() : null);
                        payload.put("error_message", event.errorMessage());
                        writeJson(target, payload);
                    }
                }))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<DataLakeIngestResponse> ingestSensorsCsv(MultipartFile file) {
        return Mono.fromCallable(() -> {
                    String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "sensors_ingest.csv";
                    Path target = bronzePath("sensors", Instant.now(), name);
                    Files.createDirectories(target.getParent());
                    Files.write(target, file.getBytes());
                    return new DataLakeIngestResponse("sensors", target.toString(), "stored");
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<DataLakeIngestResponse> ingestWeather(Map<String, Object> payload) {
        return Mono.fromCallable(() -> {
                    String date = String.valueOf(payload.getOrDefault("date", LocalDate.now(ZoneId.of("UTC"))));
                    Instant dateInstant = LocalDate.parse(date).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
                    Path target = bronzePath("weather", dateInstant, "weather_" + date + ".json");
                    writeJson(target, payload);
                    return new DataLakeIngestResponse("weather", target.toString(), "stored");
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<DataLakeStatusResponse> status() {
        return Mono.fromCallable(() -> new DataLakeStatusResponse(
                        dataLakeRoot.toString(),
                        Map.of(
                                "bronze", countFiles(dataLakeRoot.resolve("bronze")),
                                "silver", countFiles(dataLakeRoot.resolve("silver")),
                                "gold", countFiles(dataLakeRoot.resolve("gold"))
                        ),
                        readRebuildMetadata().orElse(null)
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<DataLakeRebuildResponse> rebuild() {
        return Mono.fromCallable(() -> {
                    List<BronzeClassificationEntry> classifications = readBronzeClassifications();
                    List<BronzeAlertEntry> alerts = readBronzeAlerts();
                    List<SilverClassificationEntry> silverClassifications = buildSilverClassifications(classifications);
                    List<SilverAlertEntry> silverAlerts = buildSilverAlerts(alerts);
                    List<GoldHealthIndicator> healthIndicators = buildHealthIndicators(silverClassifications);
                    List<GoldSectorSummary> sectorSummaries = buildSectorSummaries(silverClassifications, silverAlerts);
                    List<GoldDecisionContext> decisionContexts = buildDecisionContexts(sectorSummaries);

                    int silverFilesWritten = writeSilverLayers(silverClassifications, silverAlerts);
                    int goldFilesWritten = writeGoldLayers(healthIndicators, sectorSummaries, decisionContexts);
                    writeMetadata(classifications.size(), alerts.size(), silverClassifications.size(), silverAlerts.size(), healthIndicators.size(), sectorSummaries.size(), decisionContexts.size());

                    return new DataLakeRebuildResponse(
                            dataLakeRoot.toString(),
                            Instant.now().toString(),
                            classifications.size() + alerts.size(),
                            silverFilesWritten,
                            goldFilesWritten,
                            Map.of(
                                    "silverClassifications", silverClassifications.size(),
                                    "silverAlerts", silverAlerts.size(),
                                    "healthIndicators", healthIndicators.size(),
                                    "sectorSummaries", sectorSummaries.size(),
                                    "decisionContexts", decisionContexts.size()
                            )
                    );
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeOverviewResponse> loadLatestOverview() {
        return Mono.fromCallable(() -> new com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeOverviewResponse(
                        dataLakeRoot.toString(),
                        readRebuildMetadata().orElse(null),
                        Map.of(
                                "bronze", countFiles(dataLakeRoot.resolve("bronze")),
                                "silver", countFiles(dataLakeRoot.resolve("silver")),
                                "gold", countFiles(dataLakeRoot.resolve("gold"))
                        ),
                        latestJsonArray(dataLakeRoot.resolve("gold").resolve("health_indicators")),
                        latestJsonArray(dataLakeRoot.resolve("gold").resolve("sector_risk_summary")),
                        latestJsonArray(dataLakeRoot.resolve("gold").resolve("ai_reports_context"))
                ))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<BronzeClassificationEntry> readBronzeClassifications() throws IOException {
        List<BronzeClassificationEntry> out = new ArrayList<>();
        for (Path file : scanFiles(dataLakeRoot.resolve("bronze").resolve("classifications"))) {
            out.addAll(readBronzeClassificationFile(file));
        }
        return out;
    }

    private List<BronzeAlertEntry> readBronzeAlerts() throws IOException {
        List<BronzeAlertEntry> out = new ArrayList<>();
        for (Path file : scanFiles(dataLakeRoot.resolve("bronze").resolve("alerts"))) {
            out.addAll(readBronzeAlertFile(file));
        }
        return out;
    }

    private List<BronzeClassificationEntry> readBronzeClassificationFile(Path file) throws IOException {
        JsonNode root = objectMapper.readTree(file.toFile());
        if (root.isArray()) {
            List<BronzeClassificationEntry> out = new ArrayList<>();
            for (JsonNode node : root) {
                out.add(parseBronzeClassification(node));
            }
            return out;
        }
        return List.of(parseBronzeClassification(root));
    }

    private List<BronzeAlertEntry> readBronzeAlertFile(Path file) throws IOException {
        JsonNode root = objectMapper.readTree(file.toFile());
        if (root.isArray()) {
            List<BronzeAlertEntry> out = new ArrayList<>();
            for (JsonNode node : root) {
                out.add(parseBronzeAlert(node));
            }
            return out;
        }
        return List.of(parseBronzeAlert(root));
    }

    private BronzeClassificationEntry parseBronzeClassification(JsonNode node) {
        return new BronzeClassificationEntry(
                text(node, "request_id", "requestId"),
                text(node, "timestamp"),
                text(node, "source"),
                text(node, "image_name", "imageName"),
                text(node, "predicted_label", "predictedLabel"),
                text(node, "crop", "food"),
                number(node, "confidence"),
                text(node, "model_version", "modelVersion"),
                text(node, "farm_id", "farmId"),
                text(node, "sector"),
                text(node, "lot_id", "lotId")
        );
    }

    private BronzeAlertEntry parseBronzeAlert(JsonNode node) {
        return new BronzeAlertEntry(
                text(node, "id"),
                text(node, "triggered_at", "triggeredAt"),
                intValue(node, "sick_count", "sickCount"),
                intValue(node, "window_minutes", "windowMinutes"),
                intValue(node, "threshold_sick", "thresholdSick"),
                text(node, "channel"),
                text(node, "status"),
                text(node, "error_message", "errorMessage"),
                text(node, "farm_id", "farmId"),
                text(node, "sector")
        );
    }

    private List<SilverClassificationEntry> buildSilverClassifications(List<BronzeClassificationEntry> classifications) {
        Map<String, BronzeClassificationEntry> deduped = new LinkedHashMap<>();
        for (BronzeClassificationEntry entry : classifications) {
            deduped.put(entry.dedupeKey(), entry);
        }
        return deduped.values().stream()
                .map(SilverClassificationEntry::fromBronze)
                .toList();
    }

    private List<SilverAlertEntry> buildSilverAlerts(List<BronzeAlertEntry> alerts) {
        Map<String, BronzeAlertEntry> deduped = new LinkedHashMap<>();
        for (BronzeAlertEntry entry : alerts) {
            deduped.put(entry.dedupeKey(), entry);
        }
        return deduped.values().stream()
                .map(SilverAlertEntry::fromBronze)
                .toList();
    }

    private List<GoldHealthIndicator> buildHealthIndicators(List<SilverClassificationEntry> classifications) {
        Map<LocalDate, List<SilverClassificationEntry>> byDay = classifications.stream()
                .collect(Collectors.groupingBy(e -> LocalDate.ofInstant(Instant.parse(e.timestamp()), zoneId)));
        return byDay.entrySet().stream()
                .flatMap(entry -> {
                    Map<String, List<SilverClassificationEntry>> byFood = entry.getValue().stream()
                            .collect(Collectors.groupingBy(e -> Optional.ofNullable(e.food()).orElse("unknown").toLowerCase()));
                    return byFood.entrySet().stream().map(foodEntry -> {
                        long total = foodEntry.getValue().size();
                        long sick = foodEntry.getValue().stream().filter(SilverClassificationEntry::sick).count();
                        long healthy = foodEntry.getValue().stream().filter(SilverClassificationEntry::healthy).count();
                        double avgConfidence = foodEntry.getValue().stream().mapToDouble(SilverClassificationEntry::confidence).average().orElse(0.0);
                        return new GoldHealthIndicator(entry.getKey().toString(), foodEntry.getKey(), total, healthy, sick, round(avgConfidence));
                    });
                })
                .sorted(Comparator.comparing(GoldHealthIndicator::date).thenComparing(GoldHealthIndicator::crop))
                .toList();
    }

    private List<GoldSectorSummary> buildSectorSummaries(List<SilverClassificationEntry> classifications, List<SilverAlertEntry> alerts) {
        Map<String, List<SilverClassificationEntry>> byDateSector = classifications.stream()
                .collect(Collectors.groupingBy(entry -> entry.dateKey() + "|" + Optional.ofNullable(entry.sector()).orElse("unknown").toLowerCase()));
        return byDateSector.entrySet().stream()
                .map(entry -> {
                    String[] key = entry.getKey().split("\\|", 2);
                    String day = key[0];
                    String sector = key.length > 1 ? key[1] : "unknown";
                    List<SilverClassificationEntry> values = entry.getValue();
                    long total = values.size();
                    long sick = values.stream().filter(SilverClassificationEntry::sick).count();
                    long healthy = values.stream().filter(SilverClassificationEntry::healthy).count();
                    double sickRate = total == 0 ? 0.0 : (double) sick / total;
                    long alertsTriggered = alerts.stream()
                            .filter(alert -> day.equals(alert.dateKey()))
                            .filter(alert -> sector.equals(Optional.ofNullable(alert.sector()).orElse("unknown").toLowerCase()))
                            .count();
                    double avgConfidence = values.stream().mapToDouble(SilverClassificationEntry::confidence).average().orElse(0.0);
                    return new GoldSectorSummary(day, sector, Optional.ofNullable(values.get(0).farmId()).orElse("fazenda-01"), total, healthy, sick, round(sickRate), round(avgConfidence), alertsTriggered, "alto");
                })
                .sorted(Comparator.comparing(GoldSectorSummary::date).thenComparing(GoldSectorSummary::sector))
                .toList();
    }

    private List<GoldDecisionContext> buildDecisionContexts(List<GoldSectorSummary> sectors) {
        return sectors.stream()
                .map(sector -> new GoldDecisionContext(
                        sector.date(),
                        sector.farmId(),
                        sector.sector(),
                        sector.totalImages(),
                        sector.healthyCount(),
                        sector.sickCount(),
                        sector.sickRate(),
                        sector.avgConfidence(),
                        sector.alertsTriggered(),
                        sector.riskLevel(),
                        Instant.now().toString()
                ))
                .toList();
    }

    private int writeSilverLayers(List<SilverClassificationEntry> classifications, List<SilverAlertEntry> alerts) throws IOException {
        int written = 0;
        Map<String, List<SilverClassificationEntry>> byDate = classifications.stream()
                .collect(Collectors.groupingBy(SilverClassificationEntry::dateKey));
        for (var entry : byDate.entrySet()) {
            writeJson(silverClassificationFile(entry.getKey()), entry.getValue());
            written++;
        }

        Map<String, List<SilverAlertEntry>> alertsByDate = alerts.stream()
                .collect(Collectors.groupingBy(SilverAlertEntry::dateKey));
        for (var entry : alertsByDate.entrySet()) {
            writeJson(silverAlertFile(entry.getKey()), entry.getValue());
            written++;
        }
        return written;
    }

    private int writeGoldLayers(List<GoldHealthIndicator> healthIndicators, List<GoldSectorSummary> sectorSummaries, List<GoldDecisionContext> contexts) throws IOException {
        int written = 0;
        Map<String, List<GoldHealthIndicator>> byDate = healthIndicators.stream().collect(Collectors.groupingBy(GoldHealthIndicator::date));
        for (var entry : byDate.entrySet()) {
            writeJson(goldHealthFile(entry.getKey()), entry.getValue());
            written++;
        }

        Map<String, List<GoldSectorSummary>> byDateSector = sectorSummaries.stream().collect(Collectors.groupingBy(GoldSectorSummary::date));
        for (var entry : byDateSector.entrySet()) {
            writeJson(goldSectorFile(entry.getKey()), entry.getValue());
            written++;
        }

        Map<String, List<GoldDecisionContext>> byDateContext = contexts.stream().collect(Collectors.groupingBy(GoldDecisionContext::date));
        for (var entry : byDateContext.entrySet()) {
            writeJson(goldContextFile(entry.getKey()), entry.getValue());
            written++;
        }
        return written;
    }

    private void writeMetadata(int classifications, int alerts, int silverClassifications, int silverAlerts, int healthIndicators, int sectorSummaries, int contexts) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("rebuiltAt", Instant.now().toString());
        payload.put("root", dataLakeRoot.toString());
        payload.put("counts", Map.of(
                "bronzeClassifications", classifications,
                "bronzeAlerts", alerts,
                "silverClassifications", silverClassifications,
                "silverAlerts", silverAlerts,
                "goldHealthIndicators", healthIndicators,
                "goldSectorSummaries", sectorSummaries,
                "goldDecisionContexts", contexts
        ));
        writeJson(dataLakeRoot.resolve("metadata").resolve("rebuild.json"), payload);
    }

    private Optional<String> readRebuildMetadata() {
        Path meta = dataLakeRoot.resolve("metadata").resolve("rebuild.json");
        if (!Files.exists(meta)) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(meta.toFile());
            JsonNode rebuiltAt = node.get("rebuiltAt");
            return rebuiltAt == null ? Optional.empty() : Optional.of(rebuiltAt.asText());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private List<Map<String, Object>> latestJsonArray(Path dir) throws IOException {
        List<Path> files = scanFiles(dir).stream()
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .sorted(Comparator.reverseOrder())
                .toList();
        if (files.isEmpty()) {
            return List.of();
        }
        JsonNode node = objectMapper.readTree(files.getFirst().toFile());
        if (!node.isArray()) {
            return List.of(objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {}));
        }
        return objectMapper.convertValue(node, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private List<Path> scanFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private long countFiles(Path root) {
        if (!Files.exists(root)) {
            return 0L;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0L;
        }
    }

    private Path bronzePath(String domain, Instant timestamp, String fileName) {
        LocalDate date = timestamp.atZone(zoneId).toLocalDate();
        return dataLakeRoot
                .resolve("bronze")
                .resolve(domain)
                .resolve("year=" + date.getYear())
                .resolve("month=" + pad(date.getMonthValue()))
                .resolve("day=" + pad(date.getDayOfMonth()))
                .resolve(fileName);
    }

    private Path silverClassificationFile(String day) {
        LocalDate date = LocalDate.parse(day);
        return dataLakeRoot.resolve("silver").resolve("classifications_clean")
                .resolve("year=" + date.getYear())
                .resolve("month=" + pad(date.getMonthValue()))
                .resolve("day=" + pad(date.getDayOfMonth()))
                .resolve("classifications_clean_" + day + ".json");
    }

    private Path silverAlertFile(String day) {
        LocalDate date = LocalDate.parse(day);
        return dataLakeRoot.resolve("silver").resolve("alerts_clean")
                .resolve("year=" + date.getYear())
                .resolve("month=" + pad(date.getMonthValue()))
                .resolve("day=" + pad(date.getDayOfMonth()))
                .resolve("alert_events_clean_" + day + ".json");
    }

    private Path goldHealthFile(String day) {
        return dataLakeRoot.resolve("gold").resolve("health_indicators").resolve("health_indicators_" + day + ".json");
    }

    private Path goldSectorFile(String day) {
        return dataLakeRoot.resolve("gold").resolve("sector_risk_summary").resolve("sector_risk_summary_" + day + ".json");
    }

    private Path goldContextFile(String day) {
        return dataLakeRoot.resolve("gold").resolve("ai_reports_context").resolve("context_" + day + ".json");
    }

    private Path metadataFile() {
        return dataLakeRoot.resolve("metadata").resolve("rebuild.json");
    }

    private void writeJson(Path target, Object payload) throws IOException {
        Files.createDirectories(target.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), payload);
    }

    private String text(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asText();
            }
        }
        return null;
    }

    private double number(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asDouble();
            }
        }
        return 0.0;
    }

    private Integer intValue(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value != null && !value.isNull()) {
                return value.asInt();
            }
        }
        return null;
    }

    private String fileName(String prefix, String id, String suffix) {
        String safeId = (id == null || id.isBlank()) ? String.valueOf(System.currentTimeMillis()) : id;
        return prefix + "_" + safeId + suffix;
    }

    private String cropFor(ClassificationRecord record) {
        return record.food() != null && !record.food().isBlank() ? record.food() : "desconhecido";
    }

    private String sectorFor(ClassificationRecord record) {
        return "webcam".equalsIgnoreCase(record.source()) ? "estufa-02" : "estufa-01";
    }

    private String lotFor(ClassificationRecord record) {
        return "webcam".equalsIgnoreCase(record.source()) ? "lote-camera" : "lote-upload";
    }

    private String pad(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    private double round(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    private void safeRun(IoRunnable runnable) {
        try {
            runnable.run();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private interface IoRunnable {
        void run() throws IOException;
    }

    private record BronzeClassificationEntry(
            String requestId,
            String timestamp,
            String source,
            String imageName,
            String predictedLabel,
            String food,
            Double confidence,
            String modelVersion,
            String farmId,
            String sector,
            String lotId
    ) {
        String dedupeKey() {
            return requestId != null ? requestId : timestamp + ":" + imageName;
        }
    }

    private record BronzeAlertEntry(
            String id,
            String triggeredAt,
            Integer sickCount,
            Integer windowMinutes,
            Integer thresholdSick,
            String channel,
            String status,
            String errorMessage,
            String farmId,
            String sector
    ) {
        String dedupeKey() {
            return id != null ? id : triggeredAt + ":" + channel;
        }
    }

    private record SilverClassificationEntry(
            String requestId,
            String timestamp,
            String source,
            String imageName,
            String predictedLabel,
            String food,
            Double confidence,
            String modelVersion,
            String farmId,
            String sector,
            String lotId,
            boolean sick,
            boolean healthy
    ) {
        static SilverClassificationEntry fromBronze(BronzeClassificationEntry entry) {
            String label = Optional.ofNullable(entry.predictedLabel()).orElse("").trim().toLowerCase();
            String food = Optional.ofNullable(entry.food()).orElse("unknown").toLowerCase();
            return new SilverClassificationEntry(
                    entry.requestId(),
                    entry.timestamp(),
                    entry.source(),
                    entry.imageName(),
                    entry.predictedLabel(),
                    food,
                    entry.confidence(),
                    entry.modelVersion(),
                    Optional.ofNullable(entry.farmId()).orElse("fazenda-01"),
                    Optional.ofNullable(entry.sector()).orElse("unknown"),
                    Optional.ofNullable(entry.lotId()).orElse("unknown"),
                    "doente".equals(label),
                    "saudavel".equals(label)
            );
        }

        String dateKey() {
            return LocalDate.ofInstant(Instant.parse(timestamp), ZoneId.of("America/Sao_Paulo")).toString();
        }
    }

    private record SilverAlertEntry(
            String id,
            String triggeredAt,
            Integer sickCount,
            Integer windowMinutes,
            Integer thresholdSick,
            String channel,
            String status,
            String errorMessage,
            String farmId,
            String sector,
            boolean delivered
    ) {
        static SilverAlertEntry fromBronze(BronzeAlertEntry entry) {
            return new SilverAlertEntry(
                    entry.id(),
                    entry.triggeredAt(),
                    entry.sickCount(),
                    entry.windowMinutes(),
                    entry.thresholdSick(),
                    entry.channel(),
                    entry.status(),
                    entry.errorMessage(),
                    Optional.ofNullable(entry.farmId()).orElse("fazenda-01"),
                    Optional.ofNullable(entry.sector()).orElse("unknown"),
                    "SENT".equalsIgnoreCase(Optional.ofNullable(entry.status()).orElse(""))
            );
        }

        String dateKey() {
            return LocalDate.ofInstant(Instant.parse(triggeredAt), ZoneId.of("America/Sao_Paulo")).toString();
        }
    }

    private record GoldHealthIndicator(
            String date,
            String crop,
            long totalImages,
            long healthyCount,
            long sickCount,
            double avgConfidence
    ) {}

    private record GoldSectorSummary(
            String date,
            String sector,
            String farmId,
            long totalImages,
            long healthyCount,
            long sickCount,
            double sickRate,
            double avgConfidence,
            long alertsTriggered,
            String riskLevel
    ) {}

    private record GoldDecisionContext(
            String date,
            String farmId,
            String sector,
            long totalImages,
            long healthyCount,
            long sickCount,
            double sickRate,
            double avgConfidence,
            long alertsTriggered,
            String riskLevel,
            String generatedAt
    ) {}
}

package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeHealthIndicatorDTO;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeSectorSummaryDTO;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeSummaryResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class DataLakeSnapshotUseCase {

    private static final Pattern DATE_PATTERN = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private final ObjectMapper objectMapper;
    private final Path root;
    private final ZoneId zoneId;

    public DataLakeSnapshotUseCase(
            ObjectMapper objectMapper,
            @Value("${datalake.root:../datalake}") String root,
            @Value("${datalake.zoneId:America/Sao_Paulo}") String zoneId
    ) {
        this.objectMapper = objectMapper;
        this.root = Path.of(root).toAbsolutePath().normalize();
        this.zoneId = ZoneId.of(zoneId);
    }

    public Mono<DataLakeSummaryResponse> latestSummary() {
        return Mono.fromCallable(this::loadLatestSummary)
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<DataLakeSummaryResponse> summaryForDate(String date) {
        return Mono.fromCallable(() -> loadSummaryForDate(parseDate(date)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Path> saveReportMarkdown(DataLakeSummaryResponse summary, String model, String markdown) {
        return Mono.fromCallable(() -> {
                    String date = summary.latestDate() != null ? summary.latestDate() : LocalDate.now(zoneId).toString();
                    String sector = summary.availableSectors().isEmpty() ? "all" : summary.availableSectors().get(0);
                    String timestamp = Instant.now().toString().replace(":", "").replace(".", "");
                    Path target = root.resolve("gold")
                            .resolve("ai_reports_context")
                            .resolve("openai_report_" + date + "_" + sector + "_" + timestamp + ".md");
                    Files.createDirectories(target.getParent());
                    String content = "# AgroSmart AI Report\n\n"
                            + "- model: " + model + "\n"
                            + "- generated_at: " + Instant.now() + "\n"
                            + "- date: " + date + "\n\n"
                            + markdown.trim() + "\n";
                    Files.writeString(target, content, StandardCharsets.UTF_8);
                    return target;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private DataLakeSummaryResponse loadLatestSummary() throws IOException {
        LocalDate date = resolveLatestDate().orElse(LocalDate.now(zoneId));
        return loadSummaryForDate(date);
    }

    private DataLakeSummaryResponse loadSummaryForDate(LocalDate date) throws IOException {
        Map<String, Long> fileCounts = Map.of(
                "bronze", countFiles(root.resolve("bronze")),
                "silver", countFiles(root.resolve("silver")),
                "gold", countFiles(root.resolve("gold"))
        );

        String dateKey = date.toString();
        List<DataLakeHealthIndicatorDTO> healthIndicators = readHealthIndicators(dateKey);
        List<DataLakeSectorSummaryDTO> sectorSummaries = readSectorSummaries(dateKey);
        Map<String, Object> latestContext = readLatestContext();
        List<String> availableDates = resolveAvailableDates();
        List<String> availableSectors = sectorSummaries.stream()
                .map(DataLakeSectorSummaryDTO::sector)
                .filter(v -> v != null && !v.isBlank())
                .distinct()
                .sorted()
                .toList();

        return new DataLakeSummaryResponse(
                root.toString(),
                dateKey,
                readLastRebuildAt().orElse(null),
                fileCounts,
                availableDates,
                availableSectors,
                healthIndicators,
                sectorSummaries,
                latestContext
        );
    }

    private List<String> resolveAvailableDates() throws IOException {
        Set<String> dates = new LinkedHashSet<>();
        collectDates(root.resolve("gold").resolve("health_indicators"), dates);
        collectDates(root.resolve("gold").resolve("sector_risk_summary"), dates);
        return dates.stream().sorted().toList();
    }

    private void collectDates(Path dir, Set<String> dates) throws IOException {
        for (Path file : listFiles(dir)) {
            extractDate(file).ifPresent(dates::add);
        }
    }

    private Optional<LocalDate> resolveLatestDate() throws IOException {
        List<LocalDate> dates = new ArrayList<>();
        collectParsedDates(root.resolve("gold").resolve("health_indicators"), dates);
        collectParsedDates(root.resolve("gold").resolve("sector_risk_summary"), dates);
        return dates.stream().max(Comparator.naturalOrder());
    }

    private void collectParsedDates(Path dir, List<LocalDate> dates) throws IOException {
        for (Path file : listFiles(dir)) {
            extractDate(file).ifPresent(date -> dates.add(LocalDate.parse(date)));
        }
    }

    private List<DataLakeHealthIndicatorDTO> readHealthIndicators(String dateKey) throws IOException {
        Path file = findLatestMatching(root.resolve("gold").resolve("health_indicators"), dateKey, ".json");
        if (file == null) {
            return List.of();
        }
        return objectMapper.readValue(file.toFile(), new TypeReference<List<DataLakeHealthIndicatorDTO>>() {});
    }

    private List<DataLakeSectorSummaryDTO> readSectorSummaries(String dateKey) throws IOException {
        Path file = findLatestMatching(root.resolve("gold").resolve("sector_risk_summary"), dateKey, ".json");
        if (file == null) {
            return List.of();
        }
        return objectMapper.readValue(file.toFile(), new TypeReference<List<DataLakeSectorSummaryDTO>>() {});
    }

    private Map<String, Object> readLatestContext() throws IOException {
        Path file = findLatestMatching(root.resolve("gold").resolve("ai_reports_context"), null, ".json");
        if (file == null) {
            return Map.of();
        }
        JsonNode rootNode = objectMapper.readTree(file.toFile());
        if (rootNode == null || rootNode.isNull()) {
            return Map.of();
        }
        if (rootNode.isObject()) {
            return objectMapper.convertValue(rootNode, new TypeReference<LinkedHashMap<String, Object>>() {});
        }
        if (rootNode.isArray()) {
            if (rootNode.isEmpty()) {
                return Map.of();
            }
            JsonNode first = rootNode.get(0);
            if (first != null && first.isObject()) {
                return objectMapper.convertValue(first, new TypeReference<LinkedHashMap<String, Object>>() {});
            }
            return Map.of("items", objectMapper.convertValue(rootNode, new TypeReference<List<Object>>() {}));
        }
        return Map.of("value", objectMapper.convertValue(rootNode, Object.class));
    }

    private Optional<String> readLastRebuildAt() {
        Path meta = root.resolve("metadata").resolve("rebuild.json");
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

    private Path findLatestMatching(Path dir, String dateKey, String suffix) throws IOException {
        List<Path> files = listFiles(dir);
        if (files.isEmpty()) {
            return null;
        }
        Stream<Path> stream = files.stream();
        if (dateKey != null) {
            stream = stream.filter(file -> file.getFileName().toString().contains(dateKey));
        }
        stream = stream.filter(file -> file.getFileName().toString().endsWith(suffix));
        return stream.max(Comparator.comparing(file -> file.getFileName().toString())).orElse(null);
    }

    private List<Path> listFiles(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return List.of();
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).sorted().toList();
        }
    }

    private long countFiles(Path dir) {
        if (!Files.exists(dir)) {
            return 0L;
        }
        try (var stream = Files.walk(dir)) {
            return stream.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0L;
        }
    }

    private Optional<String> extractDate(Path file) {
        Matcher matcher = DATE_PATTERN.matcher(file.getFileName().toString());
        if (matcher.find()) {
            return Optional.of(matcher.group(1));
        }
        return Optional.empty();
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return LocalDate.now(zoneId);
        }
        return LocalDate.parse(date);
    }
}

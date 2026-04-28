package com.example.gatewayapi.adapters.inbound.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/iot")
public class IotController {

    private final CopyOnWriteArrayList<AutomationRuleResponse> rules = new CopyOnWriteArrayList<>(List.of(
            new AutomationRuleResponse("auto-temp-25", "Temperatura alta", "temperature", ">", 25.0, "climatizer", "Ativar climatizador", true),
            new AutomationRuleResponse("auto-hum-50", "Umidade baixa", "humidity", "<", 50.0, "humidifier", "Ativar umidificador", true),
            new AutomationRuleResponse("auto-soil-35", "Solo seco", "soilMoisture", "<", 35.0, "irrigation", "Ligar irrigacao", true),
            new AutomationRuleResponse("auto-light-850", "Luz intensa", "luminosity", ">", 850.0, "shade", "Fechar sombrites", true)
    ));

    @GetMapping("/snapshot")
    public IotSnapshotResponse snapshot() {
        List<SensorSnapshotResponse> greenhouses = buildGreenhouseSnapshots();
        SensorSnapshotResponse sensors = greenhouses.get(0);
        List<DeviceStatusResponse> devices = List.of(
                device("climatizer", "Climatizador", sensors, "temperature", ">"),
                device("humidifier", "Umidificador", sensors, "humidity", "<"),
                device("irrigation", "Irrigacao", sensors, "soilMoisture", "<"),
                device("shade", "Sombrites", sensors, "luminosity", ">")
        );
        List<AutomationEventResponse> events = rules.stream()
                .filter(rule -> rule.active() && matches(rule, sensors))
                .sorted(Comparator.comparing(AutomationRuleResponse::id))
                .map(rule -> new AutomationEventResponse(
                        UUID.nameUUIDFromBytes((rule.id() + sensors.tick()).getBytes()).toString(),
                        Instant.now().toString(),
                        rule.id(),
                        rule.name(),
                        rule.action(),
                        reading(rule.sensor(), sensors),
                        rule.operator(),
                        rule.threshold()
                ))
                .toList();

        return new IotSnapshotResponse(Instant.now().toString(), sensors, greenhouses, devices, rules, events);
    }

    @PostMapping("/automations")
    public ResponseEntity<AutomationRuleResponse> createAutomation(@RequestBody AutomationRuleRequest request) {
        String sensor = normalizeSensor(request.sensor());
        String operator = normalizeOperator(request.operator());
        String device = normalizeDevice(request.device(), sensor, operator);
        AutomationRuleResponse created = new AutomationRuleResponse(
                UUID.randomUUID().toString(),
                blankOrDefault(request.name(), "Automacao IoT"),
                sensor,
                operator,
                request.threshold(),
                device,
                blankOrDefault(request.action(), defaultAction(device)),
                true
        );
        rules.add(0, created);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/automations/{id}")
    public ResponseEntity<Void> deleteAutomation(@PathVariable String id) {
        rules.removeIf(rule -> rule.id().equals(id));
        return ResponseEntity.noContent().build();
    }

    private List<SensorSnapshotResponse> buildGreenhouseSnapshots() {
        return List.of(
                buildSensorSnapshot("estufa-01", 0.0, 1.6, -3.0, 4.0, -80.0, 0.2),
                buildSensorSnapshot("estufa-02", 4.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                buildSensorSnapshot("estufa-03", 8.0, -1.2, 5.0, 8.0, -130.0, -0.1),
                buildSensorSnapshot("estufa-04", 12.0, 2.4, -7.0, -6.0, 90.0, 0.3),
                buildSensorSnapshot("estufa-05", 16.0, -0.6, 2.0, -10.0, 150.0, -0.2)
        );
    }

    private SensorSnapshotResponse buildSensorSnapshot(
            String sector,
            double phase,
            double temperatureOffset,
            double humidityOffset,
            double soilOffset,
            double luminosityOffset,
            double phOffset
    ) {
        long tick = Instant.now().getEpochSecond();
        double wave = Math.sin((tick + phase) / 9.0);
        double fastWave = Math.cos((tick + phase) / 5.0);
        double temperature = round1(24.0 + wave * 3.2 + fastWave * 0.8);
        double humidity = round1(53.0 - wave * 8.0 + Math.sin(tick / 13.0) * 2.0);
        double soilMoisture = round1(39.0 - wave * 7.0 + Math.cos(tick / 17.0) * 3.0);
        double luminosity = Math.round(760.0 + Math.sin(tick / 7.0) * 180.0 + Math.cos(tick / 11.0) * 60.0);
        double ph = round1(6.2 + Math.sin(tick / 31.0) * 0.4);
        return new SensorSnapshotResponse(
                tick,
                sector,
                round1(temperature + temperatureOffset),
                round1(humidity + humidityOffset),
                round1(soilMoisture + soilOffset),
                Math.round(luminosity + luminosityOffset),
                round1(ph + phOffset)
        );
    }

    private DeviceStatusResponse device(String id, String name, SensorSnapshotResponse sensors, String sensor, String operator) {
        boolean active = rules.stream()
                .filter(rule -> rule.active() && rule.device().equals(id) && rule.sensor().equals(sensor) && rule.operator().equals(operator))
                .anyMatch(rule -> matches(rule, sensors));
        return new DeviceStatusResponse(id, name, active ? "ON" : "OFF", active ? "Acionado por automacao" : "Aguardando condicao");
    }

    private boolean matches(AutomationRuleResponse rule, SensorSnapshotResponse sensors) {
        double value = reading(rule.sensor(), sensors);
        return switch (rule.operator()) {
            case ">" -> value > rule.threshold();
            case ">=" -> value >= rule.threshold();
            case "<" -> value < rule.threshold();
            case "<=" -> value <= rule.threshold();
            default -> false;
        };
    }

    private double reading(String sensor, SensorSnapshotResponse sensors) {
        return switch (sensor) {
            case "temperature" -> sensors.temperature();
            case "humidity" -> sensors.humidity();
            case "soilMoisture" -> sensors.soilMoisture();
            case "luminosity" -> sensors.luminosity();
            case "ph" -> sensors.ph();
            default -> 0.0;
        };
    }

    private String normalizeSensor(String value) {
        return switch (blankOrDefault(value, "temperature")) {
            case "humidity" -> "humidity";
            case "soilMoisture" -> "soilMoisture";
            case "luminosity" -> "luminosity";
            case "ph" -> "ph";
            default -> "temperature";
        };
    }

    private String normalizeOperator(String value) {
        String operator = blankOrDefault(value, ">");
        return switch (operator) {
            case "<", "<=", ">=" -> operator;
            default -> ">";
        };
    }

    private String normalizeDevice(String value, String sensor, String operator) {
        String normalized = blankOrDefault(value, "").toLowerCase(Locale.ROOT);
        if (!normalized.isBlank()) {
            return normalized;
        }
        if ("humidity".equals(sensor) && operator.startsWith("<")) return "humidifier";
        if ("soilMoisture".equals(sensor) && operator.startsWith("<")) return "irrigation";
        if ("luminosity".equals(sensor) && operator.startsWith(">")) return "shade";
        return "climatizer";
    }

    private String defaultAction(String device) {
        return switch (device) {
            case "humidifier" -> "Ativar umidificador";
            case "irrigation" -> "Ligar irrigacao";
            case "shade" -> "Fechar sombrites";
            default -> "Ativar climatizador";
        };
    }

    private String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    public record IotSnapshotResponse(
            String generatedAt,
            SensorSnapshotResponse sensors,
            List<SensorSnapshotResponse> greenhouses,
            List<DeviceStatusResponse> devices,
            List<AutomationRuleResponse> automations,
            List<AutomationEventResponse> events
    ) {}

    public record SensorSnapshotResponse(
            long tick,
            String sector,
            double temperature,
            double humidity,
            double soilMoisture,
            double luminosity,
            double ph
    ) {}

    public record DeviceStatusResponse(String id, String name, String status, String reason) {}

    public record AutomationRuleRequest(
            String name,
            String sensor,
            String operator,
            double threshold,
            String device,
            String action
    ) {}

    public record AutomationRuleResponse(
            String id,
            String name,
            String sensor,
            String operator,
            double threshold,
            String device,
            String action,
            boolean active
    ) {}

    public record AutomationEventResponse(
            String id,
            String triggeredAt,
            String automationId,
            String automationName,
            String action,
            double currentValue,
            String operator,
            double threshold
    ) {}
}

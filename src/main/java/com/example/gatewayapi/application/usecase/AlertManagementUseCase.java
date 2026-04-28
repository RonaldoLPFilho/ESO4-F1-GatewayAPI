package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertChannelDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertConfigDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertContactDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertEventDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertStatusDTO;
import com.example.gatewayapi.adapters.outbound.db.AlertChannel;
import com.example.gatewayapi.adapters.outbound.db.AlertConfigEntity;
import com.example.gatewayapi.adapters.outbound.db.AlertContactEntity;
import com.example.gatewayapi.adapters.outbound.db.AlertEventEntity;
import com.example.gatewayapi.adapters.outbound.db.AlertStatus;
import com.example.gatewayapi.adapters.outbound.db.JpaAlertConfigRepository;
import com.example.gatewayapi.adapters.outbound.db.JpaAlertContactRepository;
import com.example.gatewayapi.adapters.outbound.db.JpaAlertEventRepository;
import com.example.gatewayapi.adapters.outbound.db.JpaClassificationResultRepository;
import com.example.gatewayapi.adapters.outbound.db.ClassificationResultEntity;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Component
public class AlertManagementUseCase {

    private static final int DEFAULT_WINDOW_MINUTES = 5;
    private static final int DEFAULT_THRESHOLD_SICK = 10;
    private static final int DEFAULT_COOLDOWN_MINUTES = 5;

    private final JpaAlertConfigRepository configRepository;
    private final JpaAlertContactRepository contactRepository;
    private final JpaAlertEventRepository eventRepository;
    private final JpaClassificationResultRepository classificationRepository;
    private final DataLakeFlowUseCase dataLakeFlowUseCase;

    public AlertManagementUseCase(
            JpaAlertConfigRepository configRepository,
            JpaAlertContactRepository contactRepository,
            JpaAlertEventRepository eventRepository,
            JpaClassificationResultRepository classificationRepository,
            DataLakeFlowUseCase dataLakeFlowUseCase
    ) {
        this.configRepository = configRepository;
        this.contactRepository = contactRepository;
        this.eventRepository = eventRepository;
        this.classificationRepository = classificationRepository;
        this.dataLakeFlowUseCase = dataLakeFlowUseCase;
    }

    public Mono<AlertConfigDTO> getConfig() {
        return Mono.fromCallable(() -> toConfigDTO(ensureConfig()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<AlertConfigDTO> saveConfig(AlertConfigDTO input) {
        return Mono.fromCallable(() -> {
                    AlertConfigEntity config = ensureConfig();
                    config.setWindowMinutes(normalizePositive(input.windowMinutes(), DEFAULT_WINDOW_MINUTES));
                    config.setThresholdSick(normalizePositive(input.thresholdSick(), DEFAULT_THRESHOLD_SICK));
                    config.setCooldownMinutes(normalizePositive(input.cooldownMinutes(), DEFAULT_COOLDOWN_MINUTES));
                    config.setActive(input.active() == null || input.active());
                    return toConfigDTO(configRepository.save(config));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Flux<AlertContactDTO> listContacts() {
        return Mono.fromCallable(() -> contactRepository.findAll().stream()
                        .sorted(Comparator.comparing(AlertContactEntity::getName, String.CASE_INSENSITIVE_ORDER))
                        .map(this::toContactDTO)
                        .toList())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<AlertContactDTO> createContact(AlertContactDTO input) {
        return Mono.fromCallable(() -> {
                    AlertContactEntity entity = new AlertContactEntity(
                            null,
                            safe(input.name()),
                            safe(input.email()),
                            safe(input.phone())
                    );
                    return toContactDTO(contactRepository.save(entity));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Void> deleteContact(String id) {
        return Mono.fromRunnable(() -> contactRepository.deleteById(UUID.fromString(id)))
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Flux<AlertEventDTO> listEvents() {
        return Mono.fromCallable(() -> eventRepository.findAllByOrderByTriggeredAtDesc().stream()
                        .map(this::toEventDTO)
                        .toList())
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<Void> triggerTest() {
        return Mono.fromRunnable(() -> {
                    AlertConfigEntity config = ensureConfig();
                    int sickCount = currentSickCount(config, Instant.now());
                    dispatchAlerts(config, sickCount, true);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    public Mono<Void> evaluateAfterClassificationSaved() {
        return Mono.fromRunnable(() -> {
                    AlertConfigEntity config = ensureConfig();
                    if (!Boolean.TRUE.equals(config.getActive())) {
                        return;
                    }

                    Instant now = Instant.now();
                    if (config.getLastTriggeredAt() != null &&
                            now.isBefore(config.getLastTriggeredAt().plus(config.getCooldownMinutes(), ChronoUnit.MINUTES))) {
                        return;
                    }

                    int sickCount = currentSickCount(config, now);
                    if (sickCount < config.getThresholdSick()) {
                        return;
                    }

                    dispatchAlerts(config, sickCount, false);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private AlertConfigEntity ensureConfig() {
        return configRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> configRepository.save(new AlertConfigEntity(
                        UUID.randomUUID(),
                        DEFAULT_WINDOW_MINUTES,
                        DEFAULT_THRESHOLD_SICK,
                        DEFAULT_COOLDOWN_MINUTES,
                        true,
                        null
                )));
    }

    private int currentSickCount(AlertConfigEntity config, Instant now) {
        Instant from = now.minus(config.getWindowMinutes(), ChronoUnit.MINUTES);
        List<ClassificationResultEntity> recent = classificationRepository.findByTimestampBetween(from, now);
        return (int) recent.stream()
                .filter(r -> r.getPredictedLabel() != null && r.getPredictedLabel().equalsIgnoreCase("doente"))
                .count();
    }

    private void dispatchAlerts(AlertConfigEntity config, int sickCount, boolean force) {
        Instant now = Instant.now();
        List<AlertContactEntity> contacts = contactRepository.findAll();
        List<AlertEventEntity> events = new ArrayList<>();

        events.add(buildEvent(
                now,
                sickCount,
                config,
                AlertChannel.EMAIL,
                contacts.stream().anyMatch(c -> !safe(c.getEmail()).isBlank()),
                force ? null : "Threshold reached"
        ));
        events.add(buildEvent(
                now,
                sickCount,
                config,
                AlertChannel.SMS,
                contacts.stream().anyMatch(c -> !safe(c.getPhone()).isBlank()),
                force ? null : "Threshold reached"
        ));

        List<AlertEventEntity> savedEvents = eventRepository.saveAll(events);
        config.setLastTriggeredAt(now);
        configRepository.save(config);
        dataLakeFlowUseCase.exportAlertEvents(
                savedEvents.stream().map(this::toEventDTO).toList(),
                "fazenda-01",
                "estufa-02"
        ).block();
    }

    private AlertEventEntity buildEvent(
            Instant triggeredAt,
            int sickCount,
            AlertConfigEntity config,
            AlertChannel channel,
            boolean hasDestination,
            String context
    ) {
        AlertStatus status = hasDestination ? AlertStatus.SENT : AlertStatus.FAILED;
        String error = hasDestination ? null : "Nenhum contato configurado para " + channel;
        if (context != null && !hasDestination) {
            error = error + " (" + context + ")";
        }
        return new AlertEventEntity(
                null,
                triggeredAt,
                sickCount,
                config.getWindowMinutes(),
                config.getThresholdSick(),
                channel,
                status,
                error
        );
    }

    private AlertConfigDTO toConfigDTO(AlertConfigEntity entity) {
        return new AlertConfigDTO(
                entity.getId().toString(),
                entity.getWindowMinutes(),
                entity.getThresholdSick(),
                entity.getCooldownMinutes(),
                entity.getActive(),
                entity.getLastTriggeredAt() != null ? entity.getLastTriggeredAt().toString() : null
        );
    }

    private AlertContactDTO toContactDTO(AlertContactEntity entity) {
        return new AlertContactDTO(
                entity.getId().toString(),
                entity.getName(),
                entity.getEmail(),
                entity.getPhone()
        );
    }

    private AlertEventDTO toEventDTO(AlertEventEntity entity) {
        return new AlertEventDTO(
                entity.getId().toString(),
                entity.getTriggeredAt().toString(),
                entity.getSickCount(),
                entity.getWindowMinutes(),
                entity.getThresholdSick(),
                AlertChannelDTO.valueOf(entity.getChannel().name()),
                AlertStatusDTO.valueOf(entity.getStatus().name()),
                entity.getErrorMessage()
        );
    }

    private int normalizePositive(Integer value, int fallback) {
        return value == null || value < 1 ? fallback : value;
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}

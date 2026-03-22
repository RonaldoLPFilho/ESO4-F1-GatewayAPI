package com.example.gatewayapi.adapters.outbound.db;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alert_events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter
public class AlertEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Instant triggeredAt;
    private Integer sickCount;
    private Integer windowMinutes;
    private Integer thresholdSick;

    @Enumerated(EnumType.STRING)
    private AlertChannel channel;

    @Enumerated(EnumType.STRING)
    private AlertStatus status;

    private String errorMessage;
}

package com.example.gatewayapi.adapters.outbound.db;

import jakarta.persistence.Entity;
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
@Table(name = "alert_config")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter
public class AlertConfigEntity {
    @Id
    private UUID id;

    private Integer windowMinutes;
    private Integer thresholdSick;
    private Integer cooldownMinutes;
    private Boolean active;
    private Instant lastTriggeredAt;
}

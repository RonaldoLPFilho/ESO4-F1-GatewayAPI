package com.example.gatewayapi.adapters.outbound.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JpaAlertEventRepository extends JpaRepository<AlertEventEntity, UUID> {
    List<AlertEventEntity> findAllByOrderByTriggeredAtDesc();
}

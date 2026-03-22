package com.example.gatewayapi.adapters.outbound.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JpaAlertConfigRepository extends JpaRepository<AlertConfigEntity, UUID> {
}

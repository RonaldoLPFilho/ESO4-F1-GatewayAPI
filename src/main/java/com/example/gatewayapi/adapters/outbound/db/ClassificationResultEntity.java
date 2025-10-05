package com.example.gatewayapi.adapters.outbound.db;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "classification_results")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Getter
@Setter(AccessLevel.PACKAGE)
public class ClassificationResultEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private Instant timestamp;
    private String source;
    private String imageName;
    private String predictedLabel;
    private String food;
    private Double confidence;
    private String modelVersion;
    private String requestId;
}
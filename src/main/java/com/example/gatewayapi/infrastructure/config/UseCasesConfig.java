package com.example.gatewayapi.infrastructure.config;

import com.example.gatewayapi.adapters.outbound.db.JpaClassificationResultRepository;
import com.example.gatewayapi.application.usecase.ClassifyImageUseCase;
import com.example.gatewayapi.application.usecase.GetDashboardOverviewUseCase;
import com.example.gatewayapi.application.usecase.QueryResultsUseCase;
import com.example.gatewayapi.application.usecase.SaveClassificationResultUseCase;
import com.example.gatewayapi.domain.port.ClassificationResultRepositoryPort;
import com.example.gatewayapi.domain.port.VisionModelPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCasesConfig {
    @Bean
    ClassifyImageUseCase classifyImageUseCase(VisionModelPort visionModelPort) {
        return new ClassifyImageUseCase(visionModelPort);
    }

    @Bean
    SaveClassificationResultUseCase saveClassificationResultUseCase(ClassificationResultRepositoryPort repo){
        return new SaveClassificationResultUseCase(repo);
    }

    @Bean
    QueryResultsUseCase queryResultsUseCase(ClassificationResultRepositoryPort repo){
        return new QueryResultsUseCase(repo);
    }

    @Bean
    GetDashboardOverviewUseCase getDashboardOverviewUseCase(JpaClassificationResultRepository repo){
        return new GetDashboardOverviewUseCase(repo);
    }
}

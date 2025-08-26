package com.example.gatewayapi.infrastructure.config;

import com.example.gatewayapi.application.usecase.ClassifyImageUseCase;
import com.example.gatewayapi.domain.port.VisionModelPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UseCasesConfig {
    @Bean
    ClassifyImageUseCase classifyImageUseCase(VisionModelPort visionModelPort) {
        return new ClassifyImageUseCase(visionModelPort);
    }
}

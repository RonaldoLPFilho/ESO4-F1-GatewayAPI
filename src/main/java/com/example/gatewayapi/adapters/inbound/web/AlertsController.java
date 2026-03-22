package com.example.gatewayapi.adapters.inbound.web;

import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertConfigDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertContactDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertEventDTO;
import com.example.gatewayapi.application.usecase.AlertManagementUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/alerts")
public class AlertsController {

    private final AlertManagementUseCase useCase;

    public AlertsController(AlertManagementUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/config")
    public Mono<ResponseEntity<AlertConfigDTO>> config() {
        return useCase.getConfig().map(ResponseEntity::ok);
    }

    @PutMapping("/config")
    public Mono<ResponseEntity<AlertConfigDTO>> saveConfig(@RequestBody AlertConfigDTO body) {
        return useCase.saveConfig(body).map(ResponseEntity::ok);
    }

    @GetMapping("/contacts")
    public Flux<AlertContactDTO> contacts() {
        return useCase.listContacts();
    }

    @PostMapping("/contacts")
    public Mono<ResponseEntity<AlertContactDTO>> createContact(@RequestBody AlertContactDTO body) {
        return useCase.createContact(body).map(ResponseEntity::ok);
    }

    @DeleteMapping("/contacts/{id}")
    public Mono<ResponseEntity<Void>> deleteContact(@PathVariable String id) {
        return useCase.deleteContact(id).thenReturn(ResponseEntity.noContent().build());
    }

    @GetMapping("/events")
    public Flux<AlertEventDTO> events() {
        return useCase.listEvents();
    }

    @PostMapping("/test")
    public Mono<ResponseEntity<Void>> test() {
        return useCase.triggerTest().thenReturn(ResponseEntity.accepted().build());
    }
}

package com.example.gatewayapi.adapters.inbound.web;

import com.example.gatewayapi.adapters.inbound.dto.ClassifyUploadResponse;
import com.example.gatewayapi.application.usecase.ClassifyImageUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/classify")
public class ClassifyController {
    private final ClassifyImageUseCase useCase;

    public ClassifyController(ClassifyImageUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<ClassifyUploadResponse>> upload(@RequestPart("image")MultipartFile image){
        final String fileName = image.getOriginalFilename() != null ? image.getOriginalFilename() : "image.jpg";

        return Mono.fromCallable(image::getBytes)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> useCase.execute(bytes, fileName))
                .map(result -> ResponseEntity.ok(
                        new ClassifyUploadResponse(
                                UUID.randomUUID().toString(),
                                fileName,
                                result.label(),
                                result.confidence(),
                                result.modelVersion(),
                                Instant.now().toString(),
                                "upload"
                        )));
    }
}

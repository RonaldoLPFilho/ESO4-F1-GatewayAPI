package com.example.gatewayapi.adapters.inbound.web;

import com.example.gatewayapi.adapters.inbound.dto.ClassifyUploadResponse;
import com.example.gatewayapi.adapters.inbound.dto.ClassifyWebcamResponse;
import com.example.gatewayapi.adapters.inbound.dto.WebCamFrameRequest;
import com.example.gatewayapi.application.usecase.ClassifyImageUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Base64;
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

        if (image == null || image.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }
        if (image.getContentType() == null || !image.getContentType().startsWith("image/")) {
            return Mono.just(ResponseEntity.status(415).build());
        }

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

    @PostMapping(value = "webcam-frame", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<ClassifyWebcamResponse>> webcamFrame(@RequestBody WebCamFrameRequest req){
        final String fileName = (req.fileName() != null && !req.fileName().isBlank()) ? req.fileName() : "frame.jpg";

        return Mono.fromCallable(() -> Base64.getDecoder().decode(req.imageBase64()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> useCase.execute(bytes, fileName))
                .map(r -> ResponseEntity.ok(new ClassifyWebcamResponse(
                        r.label(),
                        r.confidence(),
                        r.modelVersion(),
                        Instant.now().toString(),
                        "webcam"
                )));
    }
}

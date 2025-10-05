package com.example.gatewayapi.adapters.inbound.web;

import com.example.gatewayapi.adapters.inbound.dto.ClassifyUploadResponse;
import com.example.gatewayapi.adapters.inbound.dto.ClassifyWebcamResponse;
import com.example.gatewayapi.adapters.inbound.dto.WebCamFrameRequest;
import com.example.gatewayapi.application.usecase.ClassifyImageUseCase;
import com.example.gatewayapi.application.usecase.SaveClassificationResultUseCase;
import com.example.gatewayapi.domain.model.ClassificationRecord;
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
    private final SaveClassificationResultUseCase saveUseCase;

    public ClassifyController(ClassifyImageUseCase useCase, SaveClassificationResultUseCase saveUseCase) {
        this.useCase = useCase;
        this.saveUseCase = saveUseCase;
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
        final String requestId = UUID.randomUUID().toString();

        return Mono.fromCallable(image::getBytes)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> useCase.execute(bytes, fileName))
                .flatMap(result -> {
                    var record = new ClassificationRecord(
                            null,
                            Instant.now(),
                            "upload",
                            fileName,
                            result.label(),
                            result.food(),
                            result.confidence(),
                            result.modelVersion(),
                            requestId
                    );
                    return saveUseCase.execute(record)
                            .thenReturn(ResponseEntity.ok(new ClassifyUploadResponse(
                                    requestId, fileName, result.label(), result.food(), result.confidence(),
                                    result.modelVersion(), Instant.now().toString(), "upload"
                            )));
                });
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

package com.example.gatewayapi.adapters.inbound.web;

import com.example.gatewayapi.adapters.inbound.dto.ExportItemDTO;
import com.example.gatewayapi.application.usecase.QueryResultsUseCase;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/exports")
public class ExportController {
    private final QueryResultsUseCase query;

    public ExportController(QueryResultsUseCase query) {
        this.query = query;
    }

    @GetMapping("/results.json")
    public Mono<ResponseEntity<List<ExportItemDTO>>> json(){
        return query.all().map(r -> new ExportItemDTO(
                r.timestamp().toString(), r.imageName(), r.source(),
                r.predictedLabel(), r.confidence(), r.modelVersion(), r.requestId()
        ))
                .collectList()
                .map(list -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=results.json")
                        .body(list)
                );
    }

    public Mono<ResponseEntity<byte[]>> csv(){
        return query.all().map(r -> String.join(",",
                r.timestamp().toString(),
                safe(r.imageName()),
                r.source(),
                r.predictedLabel(),
                String.valueOf(r.confidence()),
                r.modelVersion(),
                r.requestId()
        ))
                .startWith("timestamp, imageName, source, predictedLabel, confidence, modelVersion, requestId")
                .collect(Collectors.joining("\n"))
                .map(csv -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=results.csv")
                        .contentType(MediaType.valueOf("text/csv"))
                        .body(csv.getBytes(StandardCharsets.UTF_8))
                );
    }

    private static String safe(String s){
        return s == null ? "" : s.replace(",", " ");
    }
}

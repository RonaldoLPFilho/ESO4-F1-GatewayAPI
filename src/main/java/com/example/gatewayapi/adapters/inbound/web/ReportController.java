package com.example.gatewayapi.adapters.inbound.web;

import com.example.gatewayapi.adapters.inbound.dto.ExportItemDTO;
import com.example.gatewayapi.adapters.inbound.dto.SumarryResponse;
import com.example.gatewayapi.adapters.outbound.client.VisionClient;
import com.example.gatewayapi.application.usecase.QueryResultsUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/reports")
public class ReportController {
    private final QueryResultsUseCase query;
    private final VisionClient visionClient;

    public ReportController(QueryResultsUseCase query, VisionClient visionClient) {
        this.query = query;
        this.visionClient = visionClient;
    }

    @GetMapping("/summary")
    public Mono<ResponseEntity<SumarryResponse>> summary(){
        var all = query.all().cache();

        Mono<Map<String, Long>> counts = all
                .groupBy(r -> r.predictedLabel())
                .flatMap(g -> g.count().map(c -> Map.entry(g.key(), c)))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);

        Mono<Double> avgConf = all
                .map(r -> r.confidence())
                .collectList()
                .map(list -> list.isEmpty()
                    ? 0.0
                    : list.stream().mapToDouble(Double::doubleValue).average().orElse(0.0)
                );

        Mono<List<ExportItemDTO>> last = all
                .sort(Comparator.comparing(r -> r.timestamp(), Comparator.reverseOrder()))
                .take(10)
                .map(r -> new ExportItemDTO(
                        r.timestamp().toString(), r.imageName(), r.source(),
                        r.predictedLabel(), r.confidence(), r.modelVersion(), r.requestId()
                ))
                .collectList();


        Mono<SumarryResponse.VisionMetrics> metrics = visionClient.metrics()
                .map(m -> new SumarryResponse.VisionMetrics(
                        m.model_version(), m.accuracy(), m.precision(), m.recall(), m.f1(), m.tested_at()
                ))
                .onErrorReturn(new SumarryResponse.VisionMetrics(null,null,null,null,null, null));

        return Mono.zip(counts, avgConf, last, metrics)
                .map(t -> ResponseEntity.ok(new SumarryResponse(t.getT1(), t.getT2(), t.getT4(), t.getT3())));
    }
}

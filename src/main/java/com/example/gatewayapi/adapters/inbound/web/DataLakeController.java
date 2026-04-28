package com.example.gatewayapi.adapters.inbound.web;

import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeIngestResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeAiReportResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeOverviewResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeAiReportRequest;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeRebuildResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeSummaryResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeStatusResponse;
import com.example.gatewayapi.application.usecase.DataLakeAiReportUseCase;
import com.example.gatewayapi.application.usecase.DataLakeFlowUseCase;
import com.example.gatewayapi.application.usecase.DataLakeSnapshotUseCase;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/datalake")
public class DataLakeController {

    private final DataLakeFlowUseCase useCase;
    private final DataLakeSnapshotUseCase snapshotUseCase;
    private final DataLakeAiReportUseCase aiReportUseCase;

    public DataLakeController(
            DataLakeFlowUseCase useCase,
            DataLakeSnapshotUseCase snapshotUseCase,
            DataLakeAiReportUseCase aiReportUseCase
    ) {
        this.useCase = useCase;
        this.snapshotUseCase = snapshotUseCase;
        this.aiReportUseCase = aiReportUseCase;
    }

    @GetMapping("/status")
    public Mono<ResponseEntity<DataLakeStatusResponse>> status() {
        return useCase.status().map(ResponseEntity::ok);
    }

    @GetMapping("/overview")
    public Mono<ResponseEntity<DataLakeOverviewResponse>> overview() {
        return useCase.loadLatestOverview().map(ResponseEntity::ok);
    }

    @GetMapping("/summary")
    public Mono<ResponseEntity<DataLakeSummaryResponse>> summary(@RequestParam(value = "date", required = false) String date) {
        return (date == null || date.isBlank()
                ? snapshotUseCase.latestSummary()
                : snapshotUseCase.summaryForDate(date))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/rebuild")
    public Mono<ResponseEntity<DataLakeRebuildResponse>> rebuild() {
        return useCase.rebuild().map(ResponseEntity::ok);
    }

    @PostMapping(value = "/ingest/sensors", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<DataLakeIngestResponse>> ingestSensors(@RequestPart("file") MultipartFile file) {
        return useCase.ingestSensorsCsv(file).map(ResponseEntity::ok);
    }

    @PostMapping(value = "/ingest/weather", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<DataLakeIngestResponse>> ingestWeather(@RequestBody Map<String, Object> payload) {
        return useCase.ingestWeather(payload).map(ResponseEntity::ok);
    }

    @GetMapping("/ai/report")
    public Mono<ResponseEntity<DataLakeAiReportResponse>> aiReportLatest() {
        return aiReportUseCase.generate(new DataLakeAiReportRequest(null, null, null, "executivo"))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/ai/report")
    public Mono<ResponseEntity<DataLakeAiReportResponse>> aiReport(@RequestBody DataLakeAiReportRequest request) {
        return aiReportUseCase.generate(request).map(ResponseEntity::ok);
    }
}

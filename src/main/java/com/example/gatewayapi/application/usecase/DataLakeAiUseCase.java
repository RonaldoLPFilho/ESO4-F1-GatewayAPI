package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeAiReportRequest;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeAiReportResponse;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@DependsOn("dataLakeAiReportUseCase")
public class DataLakeAiUseCase {

    private final DataLakeAiReportUseCase delegate;

    public DataLakeAiUseCase(DataLakeAiReportUseCase delegate) {
        this.delegate = delegate;
    }

    public Mono<DataLakeAiReportResponse> generateLatestReport() {
        return delegate.generate(new DataLakeAiReportRequest(null, null, null, "executivo"));
    }
}

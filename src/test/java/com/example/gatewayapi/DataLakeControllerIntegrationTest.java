package com.example.gatewayapi;

import com.example.gatewayapi.adapters.inbound.dto.ClassifyUploadResponse;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertChannelDTO;
import com.example.gatewayapi.adapters.inbound.dto.alerts.AlertEventDTO;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeAiReportRequest;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeAiReportResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeRebuildResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeSummaryResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeStatusResponse;
import com.example.gatewayapi.application.usecase.DataLakeFlowUseCase;
import com.example.gatewayapi.domain.model.ClassificationRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import reactor.core.publisher.Mono;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:datalake-it;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "datalake.root=target/test-datalake",
        "datalake.zoneId=America/Sao_Paulo"
})
@AutoConfigureMockMvc
class DataLakeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DataLakeFlowUseCase useCase;

    @MockBean
    private com.example.gatewayapi.adapters.outbound.client.OpenAiResponsesClient openAiResponsesClient;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDataLake() throws Exception {
        Path root = Path.of("target/test-datalake");
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            walk.sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        if (!path.equals(root)) {
                            try {
                                Files.deleteIfExists(path);
                            } catch (Exception ignored) {
                            }
                        }
                    });
        }
    }

    @Test
    void shouldExportAndRebuildDataLake() throws Exception {
        ClassificationRecord record = new ClassificationRecord(
                null,
                Instant.parse("2026-03-22T10:00:00Z"),
                "upload",
                "tomate.jpg",
                "doente",
                "tomate",
                0.91,
                "food-v2.0",
                "req-it-001"
        );
        useCase.exportClassification(record).block();

        List<AlertEventDTO> events = List.of(
                new AlertEventDTO("alert-it-001", "2026-03-22T10:01:00Z", 3, 30, 3, AlertChannelDTO.EMAIL, com.example.gatewayapi.adapters.inbound.dto.alerts.AlertStatusDTO.SENT, null),
                new AlertEventDTO("alert-it-002", "2026-03-22T10:01:00Z", 3, 30, 3, AlertChannelDTO.SMS, com.example.gatewayapi.adapters.inbound.dto.alerts.AlertStatusDTO.SENT, null)
        );
        useCase.exportAlertEvents(events, "fazenda-01", "estufa-02").block();

        DataLakeStatusResponse before = objectMapper.readValue(
                performAsync(get("/datalake/status")),
                DataLakeStatusResponse.class
        );
        assertNotNull(before);
        assertTrue(before.fileCounts().get("bronze") >= 2L);

        DataLakeRebuildResponse rebuild = objectMapper.readValue(
                performAsync(post("/datalake/rebuild")),
                DataLakeRebuildResponse.class
        );
        assertNotNull(rebuild);
        assertTrue(rebuild.bronzeRecordsExported() >= 2);
        assertTrue(rebuild.silverFilesWritten() >= 1);
        assertTrue(rebuild.goldFilesWritten() >= 1);

        DataLakeStatusResponse after = objectMapper.readValue(
                performAsync(get("/datalake/status")),
                DataLakeStatusResponse.class
        );
        assertNotNull(after.lastRebuildAt());
        assertTrue(after.fileCounts().get("gold") >= 1L);
    }

    @Test
    void shouldExposeSummaryAndAiReportEndpoints() throws Exception {
        when(openAiResponsesClient.model()).thenReturn("gpt-test");
        when(openAiResponsesClient.generateMarkdownReport(anyString(), anyString()))
                .thenReturn(Mono.just("""
                        # Resumo Executivo

                        O setor estufa-02 apresenta sinais de risco elevado.
                        """));

        for (int i = 0; i < 12; i++) {
            ClassificationRecord record = new ClassificationRecord(
                    null,
                    Instant.parse("2026-03-22T10:00:00Z").plusSeconds(i * 60L),
                    "upload",
                    "tomate-" + i + ".jpg",
                    i < 4 ? "doente" : "saudavel",
                    "tomate",
                    0.82,
                    "food-v2.0",
                    "req-ai-" + i
            );
            useCase.exportClassification(record).block();
        }
        useCase.exportAlertEvents(List.of(
                new AlertEventDTO("alert-ai-001", "2026-03-22T10:15:00Z", 4, 30, 3, AlertChannelDTO.EMAIL, com.example.gatewayapi.adapters.inbound.dto.alerts.AlertStatusDTO.SENT, null)
        ), "fazenda-01", "estufa-02").block();
        objectMapper.readValue(
                performAsync(post("/datalake/rebuild")),
                DataLakeRebuildResponse.class
        );

        DataLakeSummaryResponse summary = objectMapper.readValue(
                performAsync(get("/datalake/summary").param("date", "2026-03-22")),
                DataLakeSummaryResponse.class
        );
        assertNotNull(summary);
        assertTrue(summary.availableSectors() != null);

        DataLakeAiReportResponse report = objectMapper.readValue(
                performAsync(post("/datalake/ai/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DataLakeAiReportRequest(
                                "2026-03-22",
                                "estufa-02",
                                "tomate",
                                "executivo"
                        )))),
                DataLakeAiReportResponse.class
        );
        assertNotNull(report);
        assertTrue(report.reportMarkdown().contains("risco elevado"));
        assertTrue(report.savedPath() != null && !report.savedPath().isBlank());
    }

    @Test
    void shouldReturnSimulatedAiReportWhenDataIsInsufficient() throws Exception {
        when(openAiResponsesClient.model()).thenReturn("gpt-test");
        when(openAiResponsesClient.generateMarkdownReport(anyString(), anyString()))
                .thenReturn(Mono.just("""
                        # Resumo Executivo

                        Este relatorio apresenta um cenario demonstrativo orientado pelo contexto consolidado do Data Lake, com foco no setor estufa-02.
                        """));

        DataLakeAiReportResponse report = objectMapper.readValue(
                performAsync(post("/datalake/ai/report")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new DataLakeAiReportRequest(
                                "2026-03-22",
                                "estufa-02",
                                "tomate",
                                "executivo"
                        )))),
                DataLakeAiReportResponse.class
        );

        assertNotNull(report);
        assertTrue(report.model().contains("gpt-test"));
        assertTrue(report.reportMarkdown().contains("cenario demonstrativo"));
        assertTrue(report.savedPath() != null && !report.savedPath().isBlank());
    }

    private String performAsync(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn();
        if (!result.getRequest().isAsyncStarted()) {
            return result.getResponse().getContentAsString();
        }
        return mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }
}

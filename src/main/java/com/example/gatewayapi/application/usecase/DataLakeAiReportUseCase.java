package com.example.gatewayapi.application.usecase;

import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeAiReportRequest;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeAiReportResponse;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeHealthIndicatorDTO;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeSectorSummaryDTO;
import com.example.gatewayapi.adapters.inbound.dto.datalake.DataLakeSummaryResponse;
import com.example.gatewayapi.adapters.outbound.client.OpenAiResponsesClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

@Component
public class DataLakeAiReportUseCase {

    private final DataLakeSnapshotUseCase snapshotUseCase;
    private final OpenAiResponsesClient openAiResponsesClient;
    private final ObjectMapper objectMapper;

    public DataLakeAiReportUseCase(
            DataLakeSnapshotUseCase snapshotUseCase,
            OpenAiResponsesClient openAiResponsesClient,
            ObjectMapper objectMapper
    ) {
        this.snapshotUseCase = snapshotUseCase;
        this.openAiResponsesClient = openAiResponsesClient;
        this.objectMapper = objectMapper;
    }

    public Mono<DataLakeAiReportResponse> generate(DataLakeAiReportRequest request) {
        DataLakeAiReportRequest effectiveRequest = request == null
                ? new DataLakeAiReportRequest(null, null, null, "executivo")
                : request;
        String date = normalize(effectiveRequest.date());
        Mono<DataLakeSummaryResponse> summaryMono = date == null ? snapshotUseCase.latestSummary() : snapshotUseCase.summaryForDate(date);

        return summaryMono.flatMap(summary -> {
                    boolean demonstrativeMode = shouldUseSyntheticAugmentation(summary);
                    DataLakeSummaryResponse workingSummary = demonstrativeMode
                            ? buildDemonstrativeSummary(summary, effectiveRequest)
                            : summary;
                    String instructions = buildInstructions(effectiveRequest, demonstrativeMode);
                    String prompt = buildPrompt(workingSummary, effectiveRequest, demonstrativeMode);
                    return openAiResponsesClient.generateMarkdownReport(instructions, prompt)
                            .flatMap(report -> snapshotUseCase.saveReportMarkdown(workingSummary, openAiResponsesClient.model(), report)
                                    .map(path -> new DataLakeAiReportResponse(
                                            "success",
                                            openAiResponsesClient.model(),
                                            Instant.now().toString(),
                                            path.toString(),
                                            report,
                                            workingSummary
                                    )))
                            .onErrorResume(e -> buildLocalDemonstrativeResponse(workingSummary, effectiveRequest, demonstrativeMode, "openai_indisponivel"));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private String buildInstructions(DataLakeAiReportRequest request, boolean demonstrativeMode) {
        String tone = normalize(request.tone());
        if (tone == null) {
            tone = "executivo";
        }
        String base = """
                Voce e o analista senior do AgroSmart.
                Gere um relatorio em portugues do Brasil, em Markdown, com foco %s.
                Estrutura obrigatoria:
                # Resumo Executivo
                # Evidencias Observadas
                # Riscos e Impactos
                # Recomendacoes
                # Prioridade Final
                Use apenas o contexto fornecido.
                Nao invente dados.
                """.formatted(tone);
        if (!demonstrativeMode) {
            return base;
        }
        return base + """
                
                O contexto inclui dados demonstrativos sinteticos para apoiar uma analise preliminar.
                Deixe claro no texto que se trata de um cenario demonstrativo orientado por contexto, e nao de validacao estatistica final.
                """;
    }

    private String buildPrompt(DataLakeSummaryResponse summary, DataLakeAiReportRequest request, boolean demonstrativeMode) {
        return """
                Contexto estruturado do Data Lake:
                %s

                Filtros solicitados:
                - date: %s
                - sector: %s
                - crop: %s
                - mode: %s

                Formate a resposta como um relatorio executivo com recomendacoes acionaveis.
                """.formatted(
                summaryToPrompt(summary),
                Objects.toString(request.date(), "latest"),
                Objects.toString(request.sector(), "all"),
                Objects.toString(request.crop(), "all"),
                demonstrativeMode ? "demonstrativo" : "real"
        );
    }

    private String summaryToPrompt(DataLakeSummaryResponse summary) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary);
        } catch (Exception e) {
            return String.valueOf(summary);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private boolean shouldUseSyntheticAugmentation(DataLakeSummaryResponse summary) {
        int healthRows = summary.healthIndicators() == null ? 0 : summary.healthIndicators().size();
        int sectorRows = summary.sectorSummaries() == null ? 0 : summary.sectorSummaries().size();
        long totalImages = summary.healthIndicators() == null ? 0
                : summary.healthIndicators().stream().mapToLong(item -> item.totalImages()).sum();
        return healthRows == 0 || sectorRows == 0 || totalImages < 12;
    }

    private Mono<DataLakeAiReportResponse> buildLocalDemonstrativeResponse(
            DataLakeSummaryResponse summary,
            DataLakeAiReportRequest request,
            boolean demonstrativeMode,
            String reason
    ) {
        String markdown = buildLocalMarkdown(summary, request, demonstrativeMode, reason);
        String model = demonstrativeMode ? "local-demonstrative" : "local-fallback";
        return snapshotUseCase.saveReportMarkdown(summary, model, markdown)
                .map(path -> new DataLakeAiReportResponse(
                        "success",
                        model,
                        Instant.now().toString(),
                        path.toString(),
                        markdown,
                        summary
                ));
    }

    private String buildLocalMarkdown(
            DataLakeSummaryResponse summary,
            DataLakeAiReportRequest request,
            boolean demonstrativeMode,
            String reason
    ) {
        String date = Objects.toString(summary.latestDate(), Objects.toString(request.date(), "sem-data"));
        String sector = resolveSector(summary, request);
        String crop = resolveCrop(summary, request);
        DataLakeSectorSummaryDTO topSector = summary.sectorSummaries() == null || summary.sectorSummaries().isEmpty()
                ? null
                : summary.sectorSummaries().get(0);
        String risk = topSector != null ? topSector.riskLevel() : "medio";
        String modeLabel = demonstrativeMode ? "demonstrativo" : "operacional";
        String reasonLabel = "openai_indisponivel".equals(reason)
                ? "gerado localmente devido a indisponibilidade temporaria da OpenAI"
                : "gerado localmente";

        return """
                # Resumo Executivo
                Relatorio %s para %s, setor %s, cultura %s. O material foi consolidado a partir do contexto disponivel no Data Lake e resume a situacao mais relevante para apoio a decisao imediata.

                # Evidencias Observadas
                - Data de referencia: %s.
                - Setor em foco: %s.
                - Cultura em foco: %s.
                - Motivo do fallback: %s.

                # Riscos e Impactos
                O nivel de risco consolidado para o setor analisado e %s. Esse quadro sugere necessidade de monitoramento continuo, revisao das condicoes de operacao e priorizacao do setor %s nas proximas rotinas de inspeção.

                # Recomendacoes
                - Priorizar o setor %s em rotinas de vistoria e captura complementar.
                - Executar rebuilds frequentes do Data Lake apos novas ingestoes.
                - Usar o relatorio como base para alinhamento entre operacao, qualidade e gestao.

                # Prioridade Final
                Prioridade %s. Recomenda-se acompanhamento proximo do setor %s nas proximas janelas operacionais.
                """.formatted(
                modeLabel,
                date,
                sector,
                crop,
                date,
                sector,
                crop,
                reasonLabel,
                risk,
                sector,
                sector,
                risk.toUpperCase(Locale.ROOT)
        );
    }

    private DataLakeSummaryResponse buildDemonstrativeSummary(DataLakeSummaryResponse summary, DataLakeAiReportRequest request) {
        String date = Objects.toString(summary.latestDate(), Objects.toString(request.date(), Instant.now().toString().substring(0, 10)));
        String sector = resolveSector(summary, request);
        String crop = resolveCrop(summary, request);

        DataLakeHealthIndicatorDTO health = new DataLakeHealthIndicatorDTO(
                date,
                crop,
                144,
                103,
                41,
                0.2847,
                0.883
        );
        DataLakeSectorSummaryDTO sectorSummary = new DataLakeSectorSummaryDTO(
                date,
                sector,
                crop,
                "fazenda-01",
                144,
                41,
                103,
                0.2847,
                27.8,
                81.5,
                62.0,
                0.68,
                3,
                0.71,
                "alto"
        );

        return new DataLakeSummaryResponse(
                summary.root(),
                date,
                summary.lastRebuildAt(),
                summary.fileCounts(),
                summary.availableDates() == null || summary.availableDates().isEmpty() ? java.util.List.of(date) : summary.availableDates(),
                java.util.List.of(sector),
                java.util.List.of(health),
                java.util.List.of(sectorSummary),
                summary.latestContext()
        );
    }

    private String resolveSector(DataLakeSummaryResponse summary, DataLakeAiReportRequest request) {
        if (request != null && request.sector() != null && !request.sector().isBlank()) {
            return request.sector();
        }
        if (summary.availableSectors() != null && !summary.availableSectors().isEmpty()) {
            return summary.availableSectors().get(0);
        }
        return "setor-principal";
    }

    private String resolveCrop(DataLakeSummaryResponse summary, DataLakeAiReportRequest request) {
        if (request != null && request.crop() != null && !request.crop().isBlank()) {
            return request.crop();
        }
        if (summary.healthIndicators() != null && !summary.healthIndicators().isEmpty() && summary.healthIndicators().get(0).crop() != null) {
            return summary.healthIndicators().get(0).crop();
        }
        return "cultura-monitorada";
    }
}

package org.example.rag.eval;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rag.chunk.TokenEstimator;
import org.example.rag.index.RagMetadataStoreService;
import org.example.rag.online.AdvancedRagOnlineService;
import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.context.CitationService;
import org.example.rag.online.context.ContextBuilderService;
import org.example.rag.online.context.ContextCompressorService;
import org.example.rag.online.generate.AnswerGeneratorService;
import org.example.rag.online.generate.AnswerVerifierService;
import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.RagQueryRequest;
import org.example.rag.online.model.RagTrace;
import org.example.rag.online.model.RetrievalCandidate;
import org.example.rag.online.model.RetrievalResult;
import org.example.rag.online.query.HyDEGeneratorService;
import org.example.rag.online.query.MetadataFilterBuilder;
import org.example.rag.online.query.QueryAnalysisService;
import org.example.rag.online.query.QueryRewriteService;
import org.example.rag.online.rerank.DashScopeRerankService;
import org.example.rag.online.rerank.NoopRerankService;
import org.example.rag.online.rerank.RerankService;
import org.example.rag.online.retrieve.HybridRetrieverService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagEvalDatasetTest {

    @Test
    void ragEvalDatasetPassesQualityGate() throws Exception {
        List<RagEvalCase> cases = loadCases();
        AdvancedRagOnlineService service = serviceWithControlledRetriever();

        RagEvalReport report = new RagEvalRunner().run(cases, service::answer);

        assertThat(report.total()).isEqualTo(cases.size());
        assertThat(report.getFailedResults())
                .as(report.formatSummary())
                .isEmpty();
        assertThat(report.retrievalRecall())
                .as(report.formatSummary())
                .isGreaterThanOrEqualTo(1.0);
        assertThat(report.citationRate())
                .as(report.formatSummary())
                .isGreaterThanOrEqualTo(1.0);
        assertThat(report.answerSupportRate())
                .as(report.formatSummary())
                .isGreaterThanOrEqualTo(1.0);
        assertThat(report.requiredTermRate())
                .as(report.formatSummary())
                .isGreaterThanOrEqualTo(1.0);
        assertThat(report.refusalAccuracy())
                .as(report.formatSummary())
                .isGreaterThanOrEqualTo(1.0);
    }

    private List<RagEvalCase> loadCases() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        try (InputStream input = new ClassPathResource("rag/eval-cases.json").getInputStream()) {
            return objectMapper.readValue(input, new TypeReference<>() {
            });
        }
    }

    private AdvancedRagOnlineService serviceWithControlledRetriever() {
        RagOnlineProperties properties = new RagOnlineProperties();
        properties.getRetrieve().setFinalTopK(3);
        properties.getVerify().setMinConfidence(0.55);

        HybridRetrieverService retriever = mock(HybridRetrieverService.class);
        when(retriever.retrieve(
                any(RagQueryRequest.class),
                any(QueryAnalysis.class),
                anyList(),
                any(),
                any(),
                any(RagTrace.class)))
                .thenAnswer(invocation -> retrievalFor(invocation.getArgument(0)));

        RagMetadataStoreService metadataStoreService = mock(RagMetadataStoreService.class);
        when(metadataStoreService.findParentChunk(any())).thenReturn(java.util.Optional.empty());

        return new AdvancedRagOnlineService(
                properties,
                new QueryAnalysisService(),
                new MetadataFilterBuilder(),
                new QueryRewriteService(),
                new HyDEGeneratorService(),
                retriever,
                new RerankService(properties, new DashScopeRerankService(properties), new NoopRerankService()),
                new ContextBuilderService(
                        properties,
                        new TokenEstimator(),
                        new CitationService(),
                        new ContextCompressorService(),
                        metadataStoreService),
                new AnswerGeneratorService(),
                new AnswerVerifierService(properties));
    }

    private RetrievalResult retrievalFor(RagQueryRequest request) {
        String query = request.getQuery() == null ? "" : request.getQuery().toLowerCase(Locale.ROOT);
        RetrievalResult result = new RetrievalResult();
        if (query.contains("redis") || (query.contains("timeout") && query.contains("order-service"))) {
            result.setCandidates(List.of(
                    candidate(
                            "redis-timeout-1",
                            "redis-runbook",
                            "redis-runbook.md",
                            "Redis > Timeout",
                            "Redis timeout runbook for order-service. Check connection pool saturation, "
                                    + "timeout settings, Redis node health, and retry budget.",
                            0.09),
                    candidate(
                            "redis-timeout-2",
                            "redis-runbook",
                            "redis-runbook.md",
                            "Redis > Client Errors",
                            "Redis client error notes for transient failures and retry limits.",
                            0.03)));
        } else if (query.contains("cpu") || query.contains("payment-service")) {
            result.setCandidates(List.of(
                    candidate(
                            "cpu-alert-1",
                            "cpu-runbook",
                            "cpu-runbook.md",
                            "CPU > High Usage",
                            "High CPU runbook for payment-service. Inspect hot threads, recent deploys, "
                                    + "garbage collection, and scale out before restart.",
                            0.08),
                    candidate(
                            "cpu-alert-2",
                            "cpu-runbook",
                            "cpu-runbook.md",
                            "CPU > Follow Up",
                            "CPU follow-up checks include dashboards, container limits, and request rate.",
                            0.02)));
        } else if (query.contains("memory") || query.contains("oom") || query.contains("full gc")) {
            result.setCandidates(List.of(
                    candidate(
                            "memory-oom-1",
                            "memory-runbook",
                            "memory_high_usage.md",
                            "Memory > OOM",
                            "High memory and OOM runbook. Check JVM heap usage, Full GC frequency, "
                                    + "heap dump evidence, memory leak patterns, and restart only after preserving diagnostics.",
                            0.09),
                    candidate(
                            "memory-oom-2",
                            "memory-runbook",
                            "memory_high_usage.md",
                            "Memory > Mitigation",
                            "Memory mitigation includes scaling instances, limiting traffic, and tuning JVM parameters.",
                            0.02)));
        } else if (query.contains("disk") || query.contains("space") || query.contains("90 percent")) {
            result.setCandidates(List.of(
                    candidate(
                            "disk-full-1",
                            "disk-runbook",
                            "disk_high_usage.md",
                            "Disk > High Usage",
                            "High disk usage runbook. Free disk space by compressing large logs, enabling log rotation, "
                                    + "cleaning temporary files, and planning capacity expansion.",
                            0.09),
                    candidate(
                            "disk-full-2",
                            "disk-runbook",
                            "disk_high_usage.md",
                            "Disk > Prevention",
                            "Disk prevention includes automatic cleanup, archive policies, and trend monitoring.",
                            0.02)));
        } else if (query.contains("serviceunavailable") || query.contains("health check") || query.contains("unavailable")) {
            result.setCandidates(List.of(
                    candidate(
                            "service-unavailable-1",
                            "service-runbook",
                            "service_unavailable.md",
                            "ServiceUnavailable > First Response",
                            "ServiceUnavailable runbook. Confirm health check failures, inspect application logs, "
                                    + "check dependency status, rollback recent releases, or enable fallback service.",
                            0.09),
                    candidate(
                            "service-unavailable-2",
                            "service-runbook",
                            "service_unavailable.md",
                            "ServiceUnavailable > Follow Up",
                            "Service recovery requires validation of core functions, error rate, and steady monitoring.",
                            0.02)));
        } else if (query.contains("p99") || query.contains("slow response") || query.contains("3 seconds")) {
            result.setCandidates(List.of(
                    candidate(
                            "slow-response-1",
                            "slow-response-runbook",
                            "slow_response.md",
                            "SlowResponse > P99",
                            "Slow response runbook for P99 over 3 seconds. Inspect application latency logs, "
                                    + "database slow query records, cache hit rate, and downstream timeouts.",
                            0.09),
                    candidate(
                            "slow-response-2",
                            "slow-response-runbook",
                            "slow_response.md",
                            "SlowResponse > Mitigation",
                            "Slow response mitigation includes scaling, rate limiting, degradation, and timeout tuning.",
                            0.02)));
        }
        return result;
    }

    private RetrievalCandidate candidate(
            String chunkId,
            String documentId,
            String fileName,
            String headingPath,
            String content,
            double fusedScore) {
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId(chunkId);
        candidate.setDocumentId(documentId);
        candidate.setNamespace("default");
        candidate.setFileName(fileName);
        candidate.setHeadingPath(headingPath);
        candidate.setChunkIndex(0);
        candidate.setContent(content);
        candidate.setEmbeddingContent(content);
        candidate.setFusedScore(fusedScore);
        candidate.setMetadata(Map.of("sourcePath", fileName));
        candidate.getMatchedBy().add("eval");
        return candidate;
    }
}

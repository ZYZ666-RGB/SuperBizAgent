package org.example.rag.online;

import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.context.ContextBuilderService;
import org.example.rag.online.generate.AnswerGeneratorService;
import org.example.rag.online.generate.AnswerVerifierService;
import org.example.rag.online.model.Citation;
import org.example.rag.online.model.EvidenceContext;
import org.example.rag.online.model.RagAnswer;
import org.example.rag.online.model.RagQueryRequest;
import org.example.rag.online.model.RerankResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.example.rag.online.model.RetrievalResult;
import org.example.rag.online.model.VerificationResult;
import org.example.rag.online.query.HyDEGeneratorService;
import org.example.rag.online.query.MetadataFilterBuilder;
import org.example.rag.online.query.QueryAnalysisService;
import org.example.rag.online.query.QueryRewriteService;
import org.example.rag.online.rerank.RerankService;
import org.example.rag.online.retrieve.HybridRetrieverService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdvancedRagOnlineServiceTest {

    @Test
    void answerReturnsCitationsConfidenceAndTraceWhenEvidenceExists() {
        RagOnlineProperties properties = new RagOnlineProperties();
        HybridRetrieverService retriever = mock(HybridRetrieverService.class);
        RerankService rerankService = mock(RerankService.class);
        ContextBuilderService contextBuilderService = mock(ContextBuilderService.class);
        AnswerGeneratorService generatorService = mock(AnswerGeneratorService.class);
        AnswerVerifierService verifierService = mock(AnswerVerifierService.class);
        AdvancedRagOnlineService service = new AdvancedRagOnlineService(
                properties,
                new QueryAnalysisService(),
                new MetadataFilterBuilder(),
                new QueryRewriteService(),
                new HyDEGeneratorService(),
                retriever,
                rerankService,
                contextBuilderService,
                generatorService,
                verifierService);

        RetrievalCandidate candidate = candidate();
        RetrievalResult retrievalResult = new RetrievalResult();
        retrievalResult.setCandidates(List.of(candidate));
        when(retriever.retrieve(any(), any(), anyList(), any(), any(), any())).thenReturn(retrievalResult);
        RerankResult rerankResult = new RerankResult();
        rerankResult.setCandidates(List.of(candidate));
        rerankResult.setTimeMs(1L);
        rerankResult.setSuccess(true);
        when(rerankService.rerank(eq("Redis 超时怎么排查？"), anyList(), eq(3), any())).thenReturn(rerankResult);
        EvidenceContext evidenceContext = evidence(candidate);
        when(contextBuilderService.build(eq("Redis 超时怎么排查？"), anyList())).thenReturn(evidenceContext);
        when(generatorService.generate(eq("Redis 超时怎么排查？"), any())).thenReturn("按证据排查 Redis 超时 [1]");
        VerificationResult verification = new VerificationResult();
        verification.setSupported(true);
        verification.setConfidence(0.86);
        verification.setReason("supported");
        when(verifierService.verify(eq("Redis 超时怎么排查？"), any(), any())).thenReturn(verification);

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("Redis 超时怎么排查？");
        request.setTopK(3);
        request.setDebug(true);
        RagAnswer answer = service.answer(request);

        assertThat(answer.getSupported()).isTrue();
        assertThat(answer.getConfidence()).isEqualTo(0.86);
        assertThat(answer.getCitations()).hasSize(1);
        assertThat(answer.getTrace()).isNotNull();
        assertThat(answer.getAnswer()).contains("[1]");
    }

    @Test
    void answerRefusesWhenNoEvidenceRetrieved() {
        HybridRetrieverService retriever = mock(HybridRetrieverService.class);
        RetrievalResult retrievalResult = new RetrievalResult();
        when(retriever.retrieve(any(), any(), anyList(), any(), any(), any())).thenReturn(retrievalResult);
        AdvancedRagOnlineService service = new AdvancedRagOnlineService(
                new RagOnlineProperties(),
                new QueryAnalysisService(),
                new MetadataFilterBuilder(),
                new QueryRewriteService(),
                new HyDEGeneratorService(),
                retriever,
                mock(RerankService.class),
                mock(ContextBuilderService.class),
                mock(AnswerGeneratorService.class),
                mock(AnswerVerifierService.class));

        RagQueryRequest request = new RagQueryRequest();
        request.setQuery("知识库没有的问题");
        request.setDebug(true);
        RagAnswer answer = service.answer(request);

        assertThat(answer.getSupported()).isFalse();
        assertThat(answer.getConfidence()).isZero();
        assertThat(answer.getAnswer()).contains("知识库中没有足够信息");
        assertThat(answer.getTrace()).isNotNull();
    }

    private RetrievalCandidate candidate() {
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId("chunk-1");
        candidate.setDocumentId("doc-1");
        candidate.setFileName("runbook.md");
        candidate.setHeadingPath("Redis > Timeout");
        candidate.setContent("Redis timeout troubleshooting");
        candidate.setFusedScore(0.04);
        return candidate;
    }

    private EvidenceContext evidence(RetrievalCandidate candidate) {
        EvidenceContext context = new EvidenceContext();
        context.setContextText("[1] 文件：runbook.md\n章节：Redis > Timeout\n内容：Redis timeout troubleshooting");
        context.setUsedChunks(List.of(candidate));
        Citation citation = new Citation();
        citation.setIndex(1);
        citation.setChunkId("chunk-1");
        citation.setFileName("runbook.md");
        citation.setHeadingPath("Redis > Timeout");
        citation.setSnippet("Redis timeout troubleshooting");
        context.setCitations(List.of(citation));
        context.setTotalTokens(30);
        return context;
    }
}

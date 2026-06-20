package org.example.rag.online;

import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.context.ContextBuilderService;
import org.example.rag.online.generate.AnswerGeneratorService;
import org.example.rag.online.generate.AnswerVerifierService;
import org.example.rag.online.model.EvidenceContext;
import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.RagAnswer;
import org.example.rag.online.model.RagQueryRequest;
import org.example.rag.online.model.RagQueryResult;
import org.example.rag.online.model.RagTrace;
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
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AdvancedRagOnlineService {

    private static final String UNCOVERED_MESSAGE = "知识库中没有足够信息支撑这个问题的准确回答。你可以补充相关文档后重新提问。";

    private final RagOnlineProperties properties;
    private final QueryAnalysisService queryAnalysisService;
    private final MetadataFilterBuilder metadataFilterBuilder;
    private final QueryRewriteService queryRewriteService;
    private final HyDEGeneratorService hydeGeneratorService;
    private final HybridRetrieverService hybridRetrieverService;
    private final RerankService rerankService;
    private final ContextBuilderService contextBuilderService;
    private final AnswerGeneratorService answerGeneratorService;
    private final AnswerVerifierService answerVerifierService;

    public AdvancedRagOnlineService(
            RagOnlineProperties properties,
            QueryAnalysisService queryAnalysisService,
            MetadataFilterBuilder metadataFilterBuilder,
            QueryRewriteService queryRewriteService,
            HyDEGeneratorService hydeGeneratorService,
            HybridRetrieverService hybridRetrieverService,
            RerankService rerankService,
            ContextBuilderService contextBuilderService,
            AnswerGeneratorService answerGeneratorService,
            AnswerVerifierService answerVerifierService) {
        this.properties = properties;
        this.queryAnalysisService = queryAnalysisService;
        this.metadataFilterBuilder = metadataFilterBuilder;
        this.queryRewriteService = queryRewriteService;
        this.hydeGeneratorService = hydeGeneratorService;
        this.hybridRetrieverService = hybridRetrieverService;
        this.rerankService = rerankService;
        this.contextBuilderService = contextBuilderService;
        this.answerGeneratorService = answerGeneratorService;
        this.answerVerifierService = answerVerifierService;
    }

    public RagAnswer answer(RagQueryRequest request) {
        long start = System.currentTimeMillis();
        RagQueryRequest normalized = normalize(request);
        RagTrace trace = newTrace(normalized);
        PreparedQuery preparedQuery = prepare(normalized, trace);

        RetrievalResult retrievalResult = hybridRetrieverService.retrieve(
                normalized,
                preparedQuery.analysis(),
                preparedQuery.rewrittenQueries(),
                preparedQuery.hydeText(),
                preparedQuery.filters(),
                trace);
        if (retrievalResult.getCandidates().isEmpty()) {
            return noEvidenceAnswer(normalized, trace, start);
        }

        long rerankStart = System.currentTimeMillis();
        int finalTopK = finalTopK(normalized);
        RerankResult rerankResult = rerankService.rerank(
                normalized.getQuery(),
                retrievalResult.getCandidates(),
                finalTopK,
                normalized.getEnableRerank());
        trace.setRerankTimeMs(System.currentTimeMillis() - rerankStart);
        trace.setRerankCount(rerankResult.getCandidates().size());

        EvidenceContext evidenceContext = contextBuilderService.build(normalized.getQuery(), rerankResult.getCandidates());
        if (evidenceContext.getUsedChunks().isEmpty()) {
            return noEvidenceAnswer(normalized, trace, start);
        }

        long generationStart = System.currentTimeMillis();
        String answer = answerGeneratorService.generate(normalized.getQuery(), evidenceContext);
        trace.setGenerationTimeMs(System.currentTimeMillis() - generationStart);

        VerificationResult verification = shouldVerify(normalized)
                ? answerVerifierService.verify(normalized.getQuery(), answer, evidenceContext)
                : supportedByDefault();
        trace.setTotalTimeMs(System.currentTimeMillis() - start);

        RagAnswer ragAnswer = new RagAnswer();
        ragAnswer.setAnswer(Boolean.TRUE.equals(verification.getSupported()) ? answer : UNCOVERED_MESSAGE);
        ragAnswer.setCitations(evidenceContext.getCitations());
        ragAnswer.setUsedChunks(evidenceContext.getUsedChunks());
        ragAnswer.setSupported(verification.getSupported());
        ragAnswer.setConfidence(verification.getConfidence());
        ragAnswer.setMessage(verification.getReason());
        ragAnswer.setTrace(shouldReturnTrace(normalized) ? trace : null);
        return ragAnswer;
    }

    public RagQueryResult search(RagQueryRequest request) {
        long start = System.currentTimeMillis();
        RagQueryRequest normalized = normalize(request);
        RagTrace trace = newTrace(normalized);
        PreparedQuery preparedQuery = prepare(normalized, trace);
        RetrievalResult retrievalResult = hybridRetrieverService.retrieve(
                normalized,
                preparedQuery.analysis(),
                preparedQuery.rewrittenQueries(),
                preparedQuery.hydeText(),
                preparedQuery.filters(),
                trace);
        RerankResult rerankResult = rerankService.rerank(
                normalized.getQuery(),
                retrievalResult.getCandidates(),
                finalTopK(normalized),
                normalized.getEnableRerank());
        trace.setRerankTimeMs(rerankResult.getTimeMs());
        trace.setRerankCount(rerankResult.getCandidates().size());
        trace.setTotalTimeMs(System.currentTimeMillis() - start);

        RagQueryResult result = new RagQueryResult();
        result.setCandidates(rerankResult.getCandidates());
        result.setTrace(shouldReturnTrace(normalized) ? trace : null);
        return result;
    }

    private PreparedQuery prepare(RagQueryRequest request, RagTrace trace) {
        QueryAnalysis analysis = queryAnalysisService.analyze(request.getQuery());
        trace.setAnalysis(analysis);
        Map<String, Object> filters = metadataFilterBuilder.build(request, analysis);

        long rewriteStart = System.currentTimeMillis();
        List<String> rewrittenQueries = shouldRewrite(request, analysis)
                ? queryRewriteService.rewrite(request.getQuery(), analysis, properties.getQuery().getRewriteCount())
                : List.of(request.getQuery());
        trace.setQueryRewriteTimeMs(System.currentTimeMillis() - rewriteStart);
        trace.setRewrittenQueries(rewrittenQueries);

        String hydeText = shouldHyde(request, analysis)
                ? hydeGeneratorService.generateHypotheticalDocument(request.getQuery(), analysis)
                : "";
        trace.setHydeText(hydeText);
        return new PreparedQuery(analysis, rewrittenQueries, hydeText, filters);
    }

    private RagQueryRequest normalize(RagQueryRequest request) {
        RagQueryRequest normalized = request == null ? new RagQueryRequest() : request;
        if (normalized.getQuery() == null) {
            normalized.setQuery("");
        }
        if (normalized.getNamespace() == null || normalized.getNamespace().isBlank()) {
            normalized.setNamespace(properties.getDefaultNamespace());
        }
        if (normalized.getTopK() == null || normalized.getTopK() <= 0) {
            normalized.setTopK(properties.getRetrieve().getFinalTopK());
        }
        if (normalized.getDebug() == null) {
            normalized.setDebug(false);
        }
        return normalized;
    }

    private RagTrace newTrace(RagQueryRequest request) {
        RagTrace trace = new RagTrace();
        trace.setQuery(request.getQuery());
        return trace;
    }

    private RagAnswer noEvidenceAnswer(RagQueryRequest request, RagTrace trace, long start) {
        trace.setTotalTimeMs(System.currentTimeMillis() - start);
        RagAnswer answer = new RagAnswer();
        answer.setAnswer(UNCOVERED_MESSAGE);
        answer.setSupported(false);
        answer.setConfidence(0.0);
        answer.setMessage("No high-quality evidence was retrieved.");
        answer.setTrace(shouldReturnTrace(request) ? trace : null);
        return answer;
    }

    private boolean shouldRewrite(RagQueryRequest request, QueryAnalysis analysis) {
        boolean enabled = request.getEnableRewrite() == null
                ? properties.getQuery().isEnableRewrite()
                : request.getEnableRewrite();
        return enabled && Boolean.TRUE.equals(analysis.getNeedRewrite());
    }

    private boolean shouldHyde(RagQueryRequest request, QueryAnalysis analysis) {
        boolean enabled = request.getEnableHyde() == null
                ? properties.getQuery().isEnableHyde()
                : request.getEnableHyde();
        return enabled && Boolean.TRUE.equals(analysis.getNeedHyde());
    }

    private boolean shouldVerify(RagQueryRequest request) {
        return request.getEnableVerify() == null
                ? properties.getVerify().isEnabled()
                : request.getEnableVerify();
    }

    private boolean shouldReturnTrace(RagQueryRequest request) {
        return Boolean.TRUE.equals(request.getDebug()) && properties.getDebug().isEnableTrace();
    }

    private int finalTopK(RagQueryRequest request) {
        return Math.max(1, request.getTopK() == null ? properties.getRetrieve().getFinalTopK() : request.getTopK());
    }

    private VerificationResult supportedByDefault() {
        VerificationResult result = new VerificationResult();
        result.setSupported(true);
        result.setConfidence(0.7);
        result.setReason("Verification disabled.");
        return result;
    }

    private record PreparedQuery(
            QueryAnalysis analysis,
            List<String> rewrittenQueries,
            String hydeText,
            Map<String, Object> filters) {
    }
}

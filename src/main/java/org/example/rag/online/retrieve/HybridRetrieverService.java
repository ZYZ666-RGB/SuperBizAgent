package org.example.rag.online.retrieve;

import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.RagQueryRequest;
import org.example.rag.online.model.RagTrace;
import org.example.rag.online.model.RetrievalCandidate;
import org.example.rag.online.model.RetrievalResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class HybridRetrieverService {

    private final DenseRetrieverService denseRetrieverService;
    private final SparseRetrieverService sparseRetrieverService;
    private final RrfFusionService rrfFusionService;
    private final RagOnlineProperties properties;

    public HybridRetrieverService(
            DenseRetrieverService denseRetrieverService,
            SparseRetrieverService sparseRetrieverService,
            RrfFusionService rrfFusionService,
            RagOnlineProperties properties) {
        this.denseRetrieverService = denseRetrieverService;
        this.sparseRetrieverService = sparseRetrieverService;
        this.rrfFusionService = rrfFusionService;
        this.properties = properties;
    }

    public RetrievalResult retrieve(
            RagQueryRequest request,
            QueryAnalysis analysis,
            List<String> rewrittenQueries,
            String hydeText,
            Map<String, Object> filters,
            RagTrace trace) {
        List<List<RetrievalCandidate>> rankedLists = new ArrayList<>();
        String namespace = request.getNamespace();
        List<String> queries = rewrittenQueries == null || rewrittenQueries.isEmpty()
                ? List.of(request.getQuery())
                : rewrittenQueries;

        if (isDenseEnabled(request)) {
            long denseStart = System.currentTimeMillis();
            for (String query : queries) {
                List<RetrievalCandidate> dense = denseRetrieverService.search(
                        query, namespace, filters, properties.getRetrieve().getDenseTopK());
                dense.forEach(candidate -> addMatchedBy(candidate, "dense"));
                rankedLists.add(dense);
                trace.setDenseHitCount(trace.getDenseHitCount() + dense.size());
            }
            if (hydeText != null && !hydeText.isBlank()) {
                List<RetrievalCandidate> hyde = denseRetrieverService.search(
                        hydeText, namespace, filters, properties.getRetrieve().getDenseTopK());
                hyde.forEach(candidate -> addMatchedBy(candidate, "hyde"));
                rankedLists.add(hyde);
                trace.setDenseHitCount(trace.getDenseHitCount() + hyde.size());
            }
            trace.setDenseRetrieveTimeMs(System.currentTimeMillis() - denseStart);
        }

        if (properties.getRetrieve().isEnableSparse()) {
            long sparseStart = System.currentTimeMillis();
            for (String query : queries) {
                List<RetrievalCandidate> sparse = sparseRetrieverService.search(
                        query, namespace, filters, properties.getRetrieve().getSparseTopK(), analysis);
                sparse.forEach(candidate -> addMatchedBy(candidate, "sparse"));
                rankedLists.add(sparse);
                trace.setSparseHitCount(trace.getSparseHitCount() + sparse.size());
            }
            trace.setSparseRetrieveTimeMs(System.currentTimeMillis() - sparseStart);
        }

        List<RetrievalCandidate> fused = rrfFusionService.fuse(
                rankedLists, Math.max(requestTopK(request), properties.getRetrieve().getCandidatePoolSize()));
        trace.setFusedCount(fused.size());
        RetrievalResult result = new RetrievalResult();
        result.setCandidates(fused);
        result.setTrace(trace);
        return result;
    }

    private boolean isDenseEnabled(RagQueryRequest request) {
        if (!properties.getRetrieve().isEnableDense()) {
            return false;
        }
        if (request.getEnableHybrid() != null && !request.getEnableHybrid()) {
            return true;
        }
        return true;
    }

    private int requestTopK(RagQueryRequest request) {
        return request.getTopK() == null ? properties.getRetrieve().getFinalTopK() : request.getTopK();
    }

    private void addMatchedBy(RetrievalCandidate candidate, String value) {
        if (!candidate.getMatchedBy().contains(value)) {
            candidate.getMatchedBy().add(value);
        }
    }
}

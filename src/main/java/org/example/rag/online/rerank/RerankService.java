package org.example.rag.online.rerank;

import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.RerankResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RerankService {

    private static final Logger logger = LoggerFactory.getLogger(RerankService.class);

    private final RagOnlineProperties properties;
    private final DashScopeRerankService dashScopeRerankService;
    private final NoopRerankService noopRerankService;

    public RerankService(
            RagOnlineProperties properties,
            DashScopeRerankService dashScopeRerankService,
            NoopRerankService noopRerankService) {
        this.properties = properties;
        this.dashScopeRerankService = dashScopeRerankService;
        this.noopRerankService = noopRerankService;
    }

    public RerankResult rerank(String query, List<RetrievalCandidate> candidates, int topK, Boolean requestEnabled) {
        boolean enabled = requestEnabled == null ? properties.getRerank().isEnabled() : requestEnabled;
        if (!enabled || candidates == null || candidates.isEmpty()) {
            return noopRerankService.rerank(query, candidates, topK);
        }
        String provider = properties.getRerank().getProvider();
        if (!"dashscope".equalsIgnoreCase(provider)) {
            return noopRerankService.rerank(query, candidates, topK);
        }
        try {
            return dashScopeRerankService.rerank(query, candidates, topK);
        } catch (Exception e) {
            logger.warn("[RAG-ONLINE] Rerank provider failed, fallback to fusion ranking: {}", e.getMessage());
            RerankResult fallback = noopRerankService.rerank(query, candidates, topK);
            fallback.setSuccess(false);
            fallback.setProvider("dashscope->noop");
            fallback.setMessage(e.getMessage());
            return fallback;
        }
    }
}

package org.example.rag.online.rerank;

import org.example.rag.online.model.RerankResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DashScopeRerankService {

    public RerankResult rerank(String query, List<RetrievalCandidate> candidates, int topK) {
        throw new UnsupportedOperationException("DashScope rerank client is not configured in this build.");
    }
}

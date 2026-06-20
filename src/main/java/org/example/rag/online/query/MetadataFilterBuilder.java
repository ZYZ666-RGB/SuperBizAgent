package org.example.rag.online.query;

import org.example.rag.online.model.QueryAnalysis;
import org.example.rag.online.model.RagQueryRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MetadataFilterBuilder {

    public Map<String, Object> build(RagQueryRequest request, QueryAnalysis analysis) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (request != null && request.getFilters() != null) {
            filters.putAll(request.getFilters());
        }
        if (analysis != null && analysis.getMetadataFilter() != null) {
            analysis.getMetadataFilter().forEach(filters::putIfAbsent);
        }
        return filters;
    }
}

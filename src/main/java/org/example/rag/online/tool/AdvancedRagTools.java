package org.example.rag.online.tool;

import org.example.rag.online.AdvancedRagOnlineService;
import org.example.rag.online.model.Citation;
import org.example.rag.online.model.RagAnswer;
import org.example.rag.online.model.RagQueryRequest;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class AdvancedRagTools {

    private final AdvancedRagOnlineService advancedRagOnlineService;

    public AdvancedRagTools(AdvancedRagOnlineService advancedRagOnlineService) {
        this.advancedRagOnlineService = advancedRagOnlineService;
    }

    @Tool(description = "Use advanced RAG to search internal documents, run hybrid retrieval, build citations, and return an evidence-supported answer. Use it for system docs, operation manuals, error codes, troubleshooting, component configuration, and service dependency questions.")
    public String advancedRagSearch(
            @ToolParam(description = "Question to search in the enterprise knowledge base") String query) {
        RagQueryRequest request = new RagQueryRequest();
        request.setQuery(query);
        request.setNamespace("default");
        request.setEnableHybrid(true);
        request.setEnableRerank(true);
        request.setEnableVerify(true);
        request.setDebug(false);

        RagAnswer answer = advancedRagOnlineService.answer(request);
        String citations = answer.getCitations().stream()
                .map(this::formatCitation)
                .collect(Collectors.joining("\n"));
        return """
                Answer:
                %s

                Supported: %s
                Confidence: %.2f

                Citations:
                %s
                """.formatted(
                defaultText(answer.getAnswer(), ""),
                answer.getSupported(),
                answer.getConfidence() == null ? 0.0 : answer.getConfidence(),
                citations.isBlank() ? "No citations." : citations);
    }

    private String formatCitation(Citation citation) {
        return "[%d] %s / %s / chunkIndex=%s".formatted(
                citation.getIndex(),
                defaultText(citation.getFileName(), "unknown"),
                defaultText(citation.getHeadingPath(), "unknown"),
                citation.getChunkIndex());
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

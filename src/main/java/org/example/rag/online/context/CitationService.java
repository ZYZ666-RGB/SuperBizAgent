package org.example.rag.online.context;

import org.example.rag.online.model.Citation;
import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CitationService {

    public List<Citation> buildCitations(List<RetrievalCandidate> chunks) {
        List<Citation> citations = new ArrayList<>();
        if (chunks == null) {
            return citations;
        }
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalCandidate chunk = chunks.get(i);
            Citation citation = new Citation();
            citation.setIndex(i + 1);
            citation.setChunkId(chunk.getChunkId());
            citation.setDocumentId(chunk.getDocumentId());
            citation.setFileName(chunk.getFileName());
            citation.setHeadingPath(chunk.getHeadingPath());
            citation.setChunkIndex(chunk.getChunkIndex());
            citation.setSourcePath(chunk.getSourcePath() == null
                    ? asString(chunk.getMetadata().get("sourcePath"))
                    : chunk.getSourcePath());
            citation.setSnippet(snippet(chunk.getContent(), 180));
            citations.add(citation);
        }
        return citations;
    }

    private String snippet(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength) + "...";
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }
}

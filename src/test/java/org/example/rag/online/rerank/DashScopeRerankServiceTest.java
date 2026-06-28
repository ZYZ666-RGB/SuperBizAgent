package org.example.rag.online.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.RerankResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashScopeRerankServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsDashScopeResponseToCandidatesByIndex() {
        RagOnlineProperties properties = new RagOnlineProperties();
        DashScopeRerankService service = new DashScopeRerankService(
                properties,
                "test-key",
                "https://example.test/rerank",
                (endpoint, apiKey, body, timeoutSeconds) -> {
                    assertThat(endpoint).isEqualTo("https://example.test/rerank");
                    assertThat(apiKey).isEqualTo("test-key");
                    assertThat(timeoutSeconds).isEqualTo(properties.getRerank().getTimeoutSeconds());

                    JsonNode request = objectMapper.readTree(body);
                    assertThat(request.path("model").asText()).isEqualTo("gte-rerank-v2");
                    assertThat(request.path("input").path("query").asText()).isEqualTo("redis timeout");
                    assertThat(request.path("input").path("documents")).hasSize(2);
                    assertThat(request.path("parameters").path("top_n").asInt()).isEqualTo(2);
                    assertThat(request.path("parameters").path("return_documents").asBoolean()).isFalse();

                    return new DashScopeRerankService.HttpPoster.Response(200, """
                            {
                              "output": {
                                "results": [
                                  {"index": 1, "relevance_score": 0.93},
                                  {"index": 0, "relevance_score": 0.41}
                                ]
                              }
                            }
                            """);
                });

        RerankResult result = service.rerank("redis timeout", List.of(
                candidate("chunk-1", "less relevant"),
                candidate("chunk-2", "more relevant")), 2);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getProvider()).isEqualTo("dashscope");
        assertThat(result.getCandidates()).extracting(RetrievalCandidate::getChunkId)
                .containsExactly("chunk-2", "chunk-1");
        assertThat(result.getCandidates().get(0).getRerankScore()).isEqualTo(0.93);
        assertThat(result.getCandidates().get(1).getRerankScore()).isEqualTo(0.41);
    }

    @Test
    void rejectsMissingApiKeyBeforeHttpCall() {
        DashScopeRerankService service = new DashScopeRerankService(
                new RagOnlineProperties(),
                "",
                "https://example.test/rerank",
                (endpoint, apiKey, body, timeoutSeconds) -> {
                    throw new AssertionError("HTTP should not be called without an API key.");
                });

        assertThatThrownBy(() -> service.rerank("redis timeout", List.of(candidate("chunk-1", "content")), 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("API key");
    }

    @Test
    void rerankServiceFallsBackToNoopWhenDashScopeFails() {
        RagOnlineProperties properties = new RagOnlineProperties();
        properties.getRerank().setProvider("dashscope");
        DashScopeRerankService dashScope = new DashScopeRerankService(
                properties,
                "test-key",
                "https://example.test/rerank",
                (endpoint, apiKey, body, timeoutSeconds) -> new DashScopeRerankService.HttpPoster.Response(
                        500,
                        "{\"message\":\"temporary failure\"}"));
        RerankService service = new RerankService(properties, dashScope, new NoopRerankService());

        RetrievalCandidate weaker = candidate("chunk-1", "less relevant");
        weaker.setFusedScore(0.01);
        RetrievalCandidate stronger = candidate("chunk-2", "more relevant");
        stronger.setFusedScore(0.09);

        RerankResult result = service.rerank("redis timeout", List.of(weaker, stronger), 2, true);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getProvider()).isEqualTo("dashscope->noop");
        assertThat(result.getCandidates()).extracting(RetrievalCandidate::getChunkId)
                .containsExactly("chunk-2", "chunk-1");
        assertThat(result.getMessage()).contains("temporary failure");
    }

    private RetrievalCandidate candidate(String chunkId, String content) {
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId(chunkId);
        candidate.setDocumentId("doc-1");
        candidate.setFileName("runbook.md");
        candidate.setHeadingPath("Redis > Timeout");
        candidate.setContent(content);
        return candidate;
    }
}

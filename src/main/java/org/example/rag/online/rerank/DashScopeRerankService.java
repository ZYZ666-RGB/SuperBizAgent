package org.example.rag.online.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.RerankResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class DashScopeRerankService {

    private static final String DEFAULT_ENDPOINT =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final RagOnlineProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpPoster httpPoster;
    private final String apiKey;
    private final String endpoint;

    /*Value("${spring.ai.dashscope.api-key:}")
    private String apiKey;

    @Value("${rag.online.rerank.endpoint:" + DEFAULT_ENDPOINT + "}")
    private String endpoint;*/


   /* public DashScopeRerankService(RagOnlineProperties properties) {
        this(properties, "", DEFAULT_ENDPOINT, new JavaNetHttpPoster());
    }*/
   @Autowired
   public DashScopeRerankService(
           RagOnlineProperties properties,
           @Value("${DASHSCOPE_API_KEY:}") String apiKey,
           @Value("${rag.online.rerank.endpoint:https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank}") String endpoint
   ) {
       this(properties, apiKey, endpoint, new JavaNetHttpPoster());
   }
    public DashScopeRerankService(RagOnlineProperties properties) {
        this(
                properties,
                System.getenv().getOrDefault("DASHSCOPE_API_KEY", ""),
                DEFAULT_ENDPOINT,
                new JavaNetHttpPoster()
        );
    }

    DashScopeRerankService(
            RagOnlineProperties properties,
            String apiKey,
            String endpoint,
            HttpPoster httpPoster)
    {
        this.properties = properties;
        this.apiKey = apiKey;
        this.endpoint = endpoint;
        this.httpPoster = httpPoster;
    }

    public RerankResult rerank(String query, List<RetrievalCandidate> candidates, int topK) {
        long start = System.currentTimeMillis();
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("DashScope rerank query cannot be blank.");
        }
        if (candidates == null || candidates.isEmpty()) {
            return emptyResult(start);
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("DashScope API key is not configured.");
        }

        List<RetrievalCandidate> requestCandidates = candidates.stream()
                .filter(candidate -> !documentText(candidate).isBlank())
                .toList();
        if (requestCandidates.isEmpty()) {
            throw new IllegalArgumentException("DashScope rerank candidates do not contain text.");
        }

        try {
            String requestBody = buildRequestBody(query, requestCandidates, topK);
            HttpPoster.Response response = httpPoster.post(
                    endpoint,
                    apiKey,
                    requestBody,
                    Math.max(1, properties.getRerank().getTimeoutSeconds()));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("DashScope rerank HTTP " + response.statusCode() + ": " + response.body());
            }
            List<RetrievalCandidate> ranked = parseRankedCandidates(response.body(), requestCandidates, topK);
            if (ranked.isEmpty()) {
                throw new IllegalStateException("DashScope rerank returned no results.");
            }

            RerankResult result = new RerankResult();
            result.setCandidates(ranked);
            result.setSuccess(true);
            result.setProvider("dashscope");
            result.setMessage("DashScope rerank completed.");
            result.setTimeMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("DashScope rerank failed: " + e.getMessage(), e);
        }
    }

    private RerankResult emptyResult(long start) {
        RerankResult result = new RerankResult();
        result.setCandidates(List.of());
        result.setSuccess(true);
        result.setProvider("dashscope");
        result.setMessage("No candidates to rerank.");
        result.setTimeMs(System.currentTimeMillis() - start);
        return result;
    }

    private String buildRequestBody(String query, List<RetrievalCandidate> candidates, int topK) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getRerank().getModel());

        ObjectNode input = root.putObject("input");
        input.put("query", query);
        ArrayNode documents = input.putArray("documents");
        for (RetrievalCandidate candidate : candidates) {
            documents.add(documentText(candidate));
        }

        ObjectNode parameters = root.putObject("parameters");
        parameters.put("top_n", Math.max(1, topK));
        parameters.put("return_documents", false);
        return objectMapper.writeValueAsString(root);
    }

    private List<RetrievalCandidate> parseRankedCandidates(
            String responseBody,
            List<RetrievalCandidate> requestCandidates,
            int topK) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode results = root.path("output").path("results");
        if (!results.isArray()) {
            throw new IllegalStateException("DashScope rerank response missing output.results.");
        }

        List<RetrievalCandidate> ranked = new ArrayList<>();
        for (JsonNode item : results) {
            if (ranked.size() >= Math.max(1, topK)) {
                break;
            }
            if (!item.has("index")) {
                continue;
            }
            int index = item.path("index").asInt(-1);
            if (index < 0 || index >= requestCandidates.size()) {
                continue;
            }
            RetrievalCandidate candidate = requestCandidates.get(index);
            candidate.setRerankScore(score(item));
            ranked.add(candidate);
        }
        return ranked;
    }

    private double score(JsonNode item) {
        if (item.has("relevance_score")) {
            return item.path("relevance_score").asDouble();
        }
        if (item.has("score")) {
            return item.path("score").asDouble();
        }
        if (item.has("relevanceScore")) {
            return item.path("relevanceScore").asDouble();
        }
        return 0.0;
    }

    private String documentText(RetrievalCandidate candidate) {
        if (candidate == null) {
            return "";
        }
        String content = firstText(candidate.getContent(), candidate.getEmbeddingContent());
        String heading = candidate.getHeadingPath() == null ? "" : candidate.getHeadingPath().trim();
        if (heading.isBlank()) {
            return content;
        }
        if (content.isBlank()) {
            return heading;
        }
        return heading + "\n" + content;
    }

    private String firstText(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return "";
    }

    interface HttpPoster {
        Response post(String endpoint, String apiKey, String body, int timeoutSeconds) throws Exception;

        record Response(int statusCode, String body) {
        }
    }

    private static class JavaNetHttpPoster implements HttpPoster {
        private final HttpClient httpClient = HttpClient.newHttpClient();

        @Override
        public Response post(String endpoint, String apiKey, String body, int timeoutSeconds) throws Exception {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        }
    }
}

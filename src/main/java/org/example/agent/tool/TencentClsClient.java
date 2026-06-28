package org.example.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class TencentClsClient {

    private static final String SERVICE = "cls";
    private static final String ACTION = "SearchLog";
    private static final String ALGORITHM = "TC3-HMAC-SHA256";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter
            .ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${cls.secret-id:${TENCENT_CLOUD_SECRET_ID:}}")
    private String secretId;

    @Value("${cls.secret-key:${TENCENT_CLOUD_SECRET_KEY:}}")
    private String secretKey;

    @Value("${cls.token:${TENCENT_CLOUD_TOKEN:}}")
    private String token;

    @Value("${cls.endpoint:cls.tencentcloudapi.com}")
    private String endpoint;

    @Value("${cls.api-version:2020-10-16}")
    private String version;

    private Clock clock = Clock.systemUTC();
    private HttpPoster httpPoster = new JavaNetHttpPoster();

    public TencentClsClient() {
    }

    TencentClsClient(
            String secretId,
            String secretKey,
            String token,
            String endpoint,
            String version,
            Clock clock,
            HttpPoster httpPoster) {
        this.secretId = secretId;
        this.secretKey = secretKey;
        this.token = token;
        this.endpoint = endpoint;
        this.version = version;
        this.clock = clock;
        this.httpPoster = httpPoster;
    }

    public SearchResponse search(SearchRequest request) {
        if (isBlank(secretId) || isBlank(secretKey)) {
            throw new IllegalStateException("Tencent Cloud CLS credentials are not configured.");
        }
        if (request == null || isBlank(request.topicId())) {
            throw new IllegalArgumentException("CLS TopicId is required.");
        }

        try {
            String payload = buildPayload(request);
            Instant now = Instant.now(clock);
            Map<String, String> headers = signedHeaders(
                    ACTION,
                    defaultText(request.region(), "ap-guangzhou"),
                    payload,
                    now);
            HttpPoster.Response response = httpPoster.post(
                    endpointUrl(),
                    headers,
                    payload,
                    Math.max(1, request.timeoutSeconds()));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("CLS HTTP " + response.statusCode() + ": " + response.body());
            }
            return parseResponse(response.body());
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("CLS SearchLog failed: " + e.getMessage(), e);
        }
    }

    private String buildPayload(SearchRequest request) throws Exception {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("TopicId", request.topicId());
        root.put("From", request.from().toEpochMilli());
        root.put("To", request.to().toEpochMilli());
        root.put("QueryString", defaultText(request.queryString(), ""));
        root.put("QuerySyntax", 1);
        root.put("Limit", Math.max(1, Math.min(request.limit(), 1000)));
        root.put("Sort", "desc");
        root.put("UseNewAnalysis", true);
        root.put("HighLight", false);
        return objectMapper.writeValueAsString(root);
    }

    private SearchResponse parseResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode response = root.path("Response");
        JsonNode error = response.path("Error");
        if (!error.isMissingNode() && !error.isNull()) {
            String code = error.path("Code").asText("Unknown");
            String message = error.path("Message").asText("");
            throw new IllegalStateException("CLS error " + code + ": " + message);
        }

        List<LogRecord> records = new ArrayList<>();
        JsonNode results = response.path("Results");
        if (results.isArray()) {
            for (JsonNode item : results) {
                records.add(toRecord(item));
            }
        }
        return new SearchResponse(records, response.path("Context").asText(null), response.path("ListOver").asBoolean(false));
    }

    private LogRecord toRecord(JsonNode item) throws Exception {
        Map<String, String> fields = new LinkedHashMap<>();
        JsonNode logJson = item.path("LogJson");
        if (logJson.isTextual() && !logJson.asText().isBlank()) {
            JsonNode parsed = objectMapper.readTree(logJson.asText());
            parsed.fields().forEachRemaining(entry -> fields.put(entry.getKey(), asText(entry.getValue())));
        }
        JsonNode logFields = item.path("LogFields");
        if (logFields.isArray()) {
            for (JsonNode field : logFields) {
                String key = firstText(field.path("Name").asText(null), field.path("Key").asText(null));
                String value = firstText(field.path("Value").asText(null), field.path("value").asText(null));
                if (!isBlank(key)) {
                    fields.put(key, defaultText(value, ""));
                }
            }
        }
        if (fields.isEmpty() && item.has("RawLog")) {
            fields.put("message", item.path("RawLog").asText(""));
        }
        return new LogRecord(
                item.path("Time").asLong(0L),
                item.path("TopicId").asText(null),
                item.path("TopicName").asText(null),
                item.path("Source").asText(null),
                item.path("FileName").asText(null),
                item.path("HostName").asText(null),
                fields);
    }

    private Map<String, String> signedHeaders(String action, String region, String payload, Instant now) throws Exception {
        String host = endpointHost();
        String timestamp = String.valueOf(now.getEpochSecond());
        String date = DATE_FORMATTER.format(now);
        String contentType = "application/json; charset=utf-8";
        String signedHeaders = "content-type;host";
        String canonicalHeaders = "content-type:" + contentType + "\n"
                + "host:" + host + "\n";
        String canonicalRequest = "POST\n"
                + "/\n"
                + "\n"
                + canonicalHeaders
                + "\n"
                + signedHeaders + "\n"
                + sha256Hex(payload);
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String stringToSign = ALGORITHM + "\n"
                + timestamp + "\n"
                + credentialScope + "\n"
                + sha256Hex(canonicalRequest);

        byte[] secretDate = hmacSha256(("TC3" + secretKey).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = hmacSha256(secretService, "tc3_request");
        String signature = bytesToHex(hmacSha256(secretSigning, stringToSign));
        String authorization = ALGORITHM
                + " Credential=" + secretId + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders
                + ", Signature=" + signature;

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Content-Type", contentType);
        headers.put("Host", host);
        headers.put("X-TC-Action", action);
        headers.put("X-TC-Version", defaultText(version, "2020-10-16"));
        headers.put("X-TC-Timestamp", timestamp);
        headers.put("X-TC-Region", region);
        if (!isBlank(token)) {
            headers.put("X-TC-Token", token);
        }
        headers.put("Accept", "application/json");
        headers.put("Accept-Encoding", "gzip");
        return headers;
    }

    private String endpointHost() {
        String value = defaultText(endpoint, "cls.tencentcloudapi.com");
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return URI.create(value).getHost();
        }
        int slash = value.indexOf('/');
        return slash >= 0 ? value.substring(0, slash) : value;
    }

    private String endpointUrl() {
        String value = defaultText(endpoint, "cls.tencentcloudapi.com");
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        return "https://" + endpointHost();
    }

    private String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return bytesToHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private byte[] hmacSha256(byte[] key, String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format("%02x", b & 0xff));
        }
        return builder.toString();
    }

    private String asText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    private String firstText(String first, String second) {
        return isBlank(first) ? second : first;
    }

    private String defaultText(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record SearchRequest(
            String region,
            String topicId,
            String queryString,
            int limit,
            Instant from,
            Instant to,
            int timeoutSeconds) {
    }

    public record SearchResponse(List<LogRecord> records, String context, boolean listOver) {
    }

    public record LogRecord(
            long timeMillis,
            String topicId,
            String topicName,
            String source,
            String fileName,
            String hostName,
            Map<String, String> fields) {
    }

    interface HttpPoster {
        Response post(String endpoint, Map<String, String> headers, String body, int timeoutSeconds) throws Exception;

        record Response(int statusCode, String body) {
        }
    }

    private static class JavaNetHttpPoster implements HttpPoster {
        private final HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        @Override
        public Response post(String endpoint, Map<String, String> headers, String body, int timeoutSeconds) throws Exception {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            headers.forEach(builder::header);
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        }
    }
}

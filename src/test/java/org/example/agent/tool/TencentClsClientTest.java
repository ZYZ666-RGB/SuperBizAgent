package org.example.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TencentClsClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void signsSearchLogRequestAndParsesResults() throws Exception {
        CapturingPoster poster = new CapturingPoster("""
                {
                  "Response": {
                    "Results": [
                      {
                        "Time": 1704164645123,
                        "TopicId": "topic-id",
                        "TopicName": "application-logs",
                        "Source": "10.0.0.1",
                        "FileName": "app.log",
                        "HostName": "pod-order-1",
                        "LogJson": "{\\"level\\":\\"ERROR\\",\\"service\\":\\"order-service\\",\\"message\\":\\"boom\\"}"
                      }
                    ],
                    "Context": "next-context",
                    "ListOver": true,
                    "RequestId": "request-id"
                  }
                }
                """);
        TencentClsClient client = new TencentClsClient(
                "secret-id",
                "secret-key",
                "session-token",
                "https://cls.tencentcloudapi.com",
                "2020-10-16",
                Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC),
                poster);

        TencentClsClient.SearchResponse response = client.search(new TencentClsClient.SearchRequest(
                "ap-guangzhou",
                "topic-id",
                "level:ERROR",
                20,
                Instant.parse("2024-01-02T02:34:05Z"),
                Instant.parse("2024-01-02T03:04:05Z"),
                7));

        assertThat(poster.endpoint).isEqualTo("https://cls.tencentcloudapi.com");
        assertThat(poster.timeoutSeconds).isEqualTo(7);
        assertThat(poster.headers).containsEntry("X-TC-Action", "SearchLog");
        assertThat(poster.headers).containsEntry("X-TC-Version", "2020-10-16");
        assertThat(poster.headers).containsEntry("X-TC-Timestamp", "1704164645");
        assertThat(poster.headers).containsEntry("X-TC-Region", "ap-guangzhou");
        assertThat(poster.headers).containsEntry("X-TC-Token", "session-token");
        assertThat(poster.headers.get("Authorization"))
                .startsWith("TC3-HMAC-SHA256 Credential=secret-id/2024-01-02/cls/tc3_request");

        JsonNode body = objectMapper.readTree(poster.body);
        assertThat(body.path("TopicId").asText()).isEqualTo("topic-id");
        assertThat(body.path("From").asLong()).isEqualTo(1704162845000L);
        assertThat(body.path("To").asLong()).isEqualTo(1704164645000L);
        assertThat(body.path("QueryString").asText()).isEqualTo("level:ERROR");
        assertThat(body.path("QuerySyntax").asInt()).isEqualTo(1);
        assertThat(body.path("Limit").asInt()).isEqualTo(20);
        assertThat(body.path("UseNewAnalysis").asBoolean()).isTrue();

        assertThat(response.context()).isEqualTo("next-context");
        assertThat(response.listOver()).isTrue();
        assertThat(response.records()).hasSize(1);
        TencentClsClient.LogRecord record = response.records().get(0);
        assertThat(record.timeMillis()).isEqualTo(1704164645123L);
        assertThat(record.topicId()).isEqualTo("topic-id");
        assertThat(record.topicName()).isEqualTo("application-logs");
        assertThat(record.source()).isEqualTo("10.0.0.1");
        assertThat(record.hostName()).isEqualTo("pod-order-1");
        assertThat(record.fields()).containsEntry("level", "ERROR")
                .containsEntry("service", "order-service")
                .containsEntry("message", "boom");
    }

    @Test
    void throwsTencentCloudErrorMessage() {
        TencentClsClient client = new TencentClsClient(
                "secret-id",
                "secret-key",
                "",
                "cls.tencentcloudapi.com",
                "2020-10-16",
                Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC),
                new CapturingPoster("""
                        {
                          "Response": {
                            "Error": {
                              "Code": "AuthFailure.SignatureFailure",
                              "Message": "bad signature"
                            },
                            "RequestId": "request-id"
                          }
                        }
                        """));

        assertThatThrownBy(() -> client.search(new TencentClsClient.SearchRequest(
                "ap-guangzhou",
                "topic-id",
                "*",
                20,
                Instant.parse("2024-01-02T02:34:05Z"),
                Instant.parse("2024-01-02T03:04:05Z"),
                7)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AuthFailure.SignatureFailure")
                .hasMessageContaining("bad signature");
    }

    private static class CapturingPoster implements TencentClsClient.HttpPoster {
        private final String responseBody;
        private String endpoint;
        private Map<String, String> headers;
        private String body;
        private int timeoutSeconds;

        private CapturingPoster(String responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public Response post(String endpoint, Map<String, String> headers, String body, int timeoutSeconds) {
            this.endpoint = endpoint;
            this.headers = headers;
            this.body = body;
            this.timeoutSeconds = timeoutSeconds;
            return new Response(200, responseBody);
        }
    }
}

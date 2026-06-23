package org.example.aiops;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiOpsDecisionParserTest {

    private final AiOpsDecisionParser parser = new AiOpsDecisionParser();

    @Test
    void parsesPlannerDecisionFromFencedJson() {
        String text = """
                ```json
                {
                  "decision": "EXECUTE",
                  "step": "查询 Prometheus 活跃告警",
                  "rationale": "需要先确认当前告警面",
                  "expectedEvidence": "活动告警列表",
                  "toolName": "queryPrometheusAlerts",
                  "toolParameters": {
                    "region": "ap-guangzhou"
                  }
                }
                ```
                """;

        AiOpsDecision decision = parser.parseDecision(text);

        assertThat(decision.getDecision()).isEqualTo(AiOpsDecisionType.EXECUTE);
        assertThat(decision.getStep()).isEqualTo("查询 Prometheus 活跃告警");
        assertThat(decision.getToolName()).isEqualTo("queryPrometheusAlerts");
        assertThat(decision.getToolParameters()).containsEntry("region", "ap-guangzhou");
    }

    @Test
    void nonJsonPlannerOutputFallsBackToExecutableStep() {
        AiOpsDecision decision = parser.parseDecision("先查询活跃告警，然后看日志。");

        assertThat(decision.getDecision()).isEqualTo(AiOpsDecisionType.EXECUTE);
        assertThat(decision.getStep()).contains("先查询活跃告警");
        assertThat(decision.getRationale()).contains("non-JSON");
    }

    @Test
    void parsesExecutorResultWithKeyFindings() {
        String text = """
                {
                  "status": "SUCCESS",
                  "summary": "发现 HighCPUUsage 告警",
                  "keyFindings": ["payment-service CPU 92%", "持续 25 分钟"],
                  "evidence": "Prometheus active alerts returned HighCPUUsage.",
                  "nextHint": "查询 system-metrics 日志"
                }
                """;

        AiOpsExecutionResult result = parser.parseExecutionResult(text);

        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getSummary()).contains("HighCPUUsage");
        assertThat(result.getKeyFindings()).containsExactly("payment-service CPU 92%", "持续 25 分钟");
        assertThat(result.getEvidence()).contains("Prometheus");
        assertThat(result.getNextHint()).contains("system-metrics");
    }
}

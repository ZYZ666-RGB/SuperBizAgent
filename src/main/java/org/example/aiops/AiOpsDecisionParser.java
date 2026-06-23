package org.example.aiops;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AiOpsDecisionParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public AiOpsDecision parseDecision(String text) {
        AiOpsDecision decision = new AiOpsDecision();
        decision.setRawText(text);
        String json = extractJson(text);
        if (json == null) {
            decision.setDecision(AiOpsDecisionType.EXECUTE);
            decision.setStep(defaultText(text, "执行下一步排查。"));
            decision.setRationale("Planner returned non-JSON output; treated as an executable step.");
            return decision;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            decision.setDecision(parseDecisionType(textValue(root, "decision")));
            decision.setStep(firstText(root, "step", "nextStep", "action"));
            decision.setRationale(firstText(root, "rationale", "reason", "analysis"));
            decision.setExpectedEvidence(firstText(root, "expectedEvidence", "expected_evidence", "evidenceNeed"));
            decision.setToolName(firstText(root, "toolName", "tool", "expectedTool"));
            decision.setFailureReason(firstText(root, "failureReason", "failure_reason", "error"));
            JsonNode parameters = firstNode(root, "toolParameters", "tool_parameters", "params", "arguments");
            if (parameters != null && parameters.isObject()) {
                decision.setToolParameters(objectMapper.convertValue(parameters, new TypeReference<>() {
                }));
            }
            return decision;
        } catch (Exception e) {
            decision.setDecision(AiOpsDecisionType.EXECUTE);
            decision.setStep(defaultText(text, "执行下一步排查。"));
            decision.setRationale("Planner JSON could not be parsed: " + e.getMessage());
            return decision;
        }
    }

    public AiOpsExecutionResult parseExecutionResult(String text) {
        AiOpsExecutionResult result = new AiOpsExecutionResult();
        result.setRawText(text);
        String json = extractJson(text);
        if (json == null) {
            result.setStatus("SUCCESS");
            result.setSummary(defaultText(text, ""));
            result.setEvidence(defaultText(text, ""));
            return result;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            result.setStatus(defaultText(firstText(root, "status", "state"), "UNKNOWN"));
            result.setSummary(firstText(root, "summary", "message", "result"));
            result.setEvidence(firstText(root, "evidence", "proof", "details"));
            result.setNextHint(firstText(root, "nextHint", "next_hint", "recommendation"));
            result.setKeyFindings(stringList(firstNode(root, "keyFindings", "key_findings", "findings")));
            result.setMetadata(rootToMap(root));
            return result;
        } catch (Exception e) {
            result.setStatus("SUCCESS");
            result.setSummary(defaultText(text, ""));
            result.setEvidence(defaultText(text, ""));
            result.getMetadata().put("parseError", e.getMessage());
            return result;
        }
    }

    public String extractJson(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = stripCodeFence(text.trim());
        int start = stripped.indexOf('{');
        if (start < 0) {
            return null;
        }
        int depth = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < stripped.length(); i++) {
            char ch = stripped.charAt(i);
            if (escaped) {
                escaped = false;
                continue;
            }
            if (ch == '\\') {
                escaped = true;
                continue;
            }
            if (ch == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return stripped.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private AiOpsDecisionType parseDecisionType(String value) {
        if (value == null || value.isBlank()) {
            return AiOpsDecisionType.EXECUTE;
        }
        try {
            return AiOpsDecisionType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return AiOpsDecisionType.EXECUTE;
        }
    }

    private String stripCodeFence(String text) {
        if (!text.startsWith("```")) {
            return text;
        }
        String stripped = text.replaceFirst("^```[a-zA-Z]*\\s*", "");
        int end = stripped.lastIndexOf("```");
        return end >= 0 ? stripped.substring(0, end).trim() : stripped.trim();
    }

    private JsonNode firstNode(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode node = root.get(name);
            if (node != null && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... names) {
        JsonNode node = firstNode(root, names);
        if (node == null) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isValueNode()) {
            return node.asText();
        }
        return node.toString();
    }

    private String textValue(JsonNode root, String name) {
        JsonNode node = root.get(name);
        return node == null || node.isNull() ? null : node.asText();
    }

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || node.isNull()) {
            return values;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                values.add(item.isValueNode() ? item.asText() : item.toString());
            }
            return values;
        }
        values.add(node.isValueNode() ? node.asText() : node.toString());
        return values;
    }

    private Map<String, Object> rootToMap(JsonNode root) {
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.put(field.getKey(), objectMapper.convertValue(field.getValue(), Object.class));
        }
        return result;
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

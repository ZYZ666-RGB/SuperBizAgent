package org.example.memory;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MemoryExtractorService {

    private static final Logger logger = LoggerFactory.getLogger(MemoryExtractorService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MemoryCandidate> extract(String question, String answer, DashScopeChatModel chatModel) {
        if (containsExplicitSaveIntent(question)) {
            return List.of(explicitCandidate(question));
        }
        if (chatModel == null) {
            return List.of();
        }

        try {
            ChatResponse response = chatModel.call(new Prompt(buildPrompt(question, answer)));
            if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
                return List.of();
            }
            String text = response.getResult().getOutput().getText();
            return parseCandidates(text);
        } catch (Exception e) {
            logger.warn("Failed to extract long-term memory: {}", e.getMessage(), e);
            return List.of();
        }
    }

    List<MemoryCandidate> parseCandidates(String text) throws Exception {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String json = extractJsonArray(text);
        if (json == null) {
            return List.of();
        }
        return objectMapper.readValue(json, new TypeReference<>() {
        });
    }

    private MemoryCandidate explicitCandidate(String question) {
        MemoryCandidate candidate = new MemoryCandidate();
        candidate.setMemoryType(guessMemoryType(question));
        candidate.setScopeType("project_context".equals(candidate.getMemoryType()) ? "project" : "user");
        candidate.setContent(cleanExplicitContent(question));
        candidate.setEvidence(question);
        candidate.setExplicitSave(true);
        candidate.setSource("user_explicit");
        candidate.setEvidenceScore(1.0);
        candidate.setStabilityScore(0.9);
        candidate.setFutureUsefulnessScore(0.9);
        candidate.setSafetyScore(1);
        candidate.setImportance(0.85);
        candidate.setConfidence(0.95);
        candidate.setShouldSave(true);
        candidate.setReason("User explicitly asked to save this memory.");
        return candidate;
    }

    private boolean containsExplicitSaveIntent(String question) {
        if (question == null) {
            return false;
        }
        return question.contains("记住")
                || question.contains("记下来")
                || question.contains("保存到记忆")
                || question.contains("加入记忆")
                || question.contains("以后回答")
                || question.contains("以后都")
                || question.contains("以后请");
    }

    private String cleanExplicitContent(String question) {
        if (question == null) {
            return "";
        }
        return question.trim()
                .replaceFirst("^(请)?(帮我)?(记住|记下来)[，,:：\\s]*", "")
                .replace("把这个加入记忆", "")
                .replace("保存到记忆", "")
                .trim();
    }

    private String guessMemoryType(String question) {
        if (question == null) {
            return "semantic";
        }
        if (question.contains("SuperBizAgent") || question.contains("项目") || question.contains("技术栈")) {
            return "project_context";
        }
        if (question.contains("偏好") || question.contains("喜欢") || question.contains("希望")
                || question.contains("优先") || question.contains("以后回答")) {
            return "preference";
        }
        if (question.contains("环境") || question.contains("Windows") || question.contains("Mac")
                || question.contains("Linux")) {
            return "environment";
        }
        return "semantic";
    }

    private String extractJsonArray(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }
        int start = cleaned.indexOf('[');
        int end = cleaned.lastIndexOf(']');
        if (start < 0 || end < start) {
            return null;
        }
        return cleaned.substring(start, end + 1);
    }

    private String buildPrompt(String question, String answer) {
        return """
                请从下面的对话中判断是否存在值得长期保存的用户记忆。

                长期记忆必须满足：
                1. 用户明确表达过，不能基于模型猜测；
                2. 对未来多次回答有帮助；
                3. 相对稳定，不是临时情绪、临时问题、一次性状态；
                4. 不包含敏感隐私、密码、密钥、身份证号、手机号、地址等信息；
                5. 属于当前用户本人、当前项目、当前任务或当前 Agent 场景。

                用户主动保存优先级最高：
                如果用户明确说“记住”“以后都”“保存到记忆”“加入记忆”，则 explicitSave=true。

                允许的记忆类型：
                - preference
                - project_context
                - career_goal
                - skill
                - environment
                - task
                - episodic
                - semantic

                请严格返回 JSON 数组：
                [
                  {
                    "memoryType": "...",
                    "scopeType": "user/session/project/task",
                    "content": "...",
                    "evidence": "原文证据",
                    "entities": ["..."],
                    "explicitSave": true,
                    "source": "user_explicit/auto_extracted",
                    "evidenceScore": 0.0,
                    "stabilityScore": 0.0,
                    "futureUsefulnessScore": 0.0,
                    "safetyScore": 1,
                    "importance": 0.0,
                    "confidence": 0.0,
                    "shouldSave": true,
                    "reason": "保存或不保存的原因"
                  }
                ]

                如果没有值得保存的信息，返回 []。

                用户输入：
                %s

                助手回答：
                %s
                """.formatted(question == null ? "" : question, answer == null ? "" : answer);
    }
}

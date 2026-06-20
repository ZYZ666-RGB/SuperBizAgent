package org.example.rag.online.generate;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.example.rag.online.model.EvidenceContext;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AnswerGeneratorService {

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${rag.model:qwen3-max}")
    private String model;

    public String generate(String query, EvidenceContext context) {
        if (context == null || context.getContextText() == null || context.getContextText().isBlank()) {
            return "知识库中没有足够信息支撑这个问题的准确回答。你可以补充相关文档后重新提问。";
        }
        String prompt = buildPrompt(query, context);
        if (dashScopeApiKey != null && !dashScopeApiKey.isBlank()) {
            try {
                DashScopeChatModel chatModel = DashScopeChatModel.builder()
                        .dashScopeApi(DashScopeApi.builder().apiKey(dashScopeApiKey).build())
                        .defaultOptions(DashScopeChatOptions.builder()
                                .withModel(model)
                                .withTemperature(0.2)
                                .withMaxToken(1800)
                                .withTopP(0.8)
                                .build())
                        .build();
                ChatResponse response = chatModel.call(new Prompt(prompt));
                if (response != null && response.getResult() != null && response.getResult().getOutput() != null) {
                    String text = response.getResult().getOutput().getText();
                    if (text != null && !text.isBlank()) {
                        return text.trim();
                    }
                }
            } catch (Exception ignored) {
                return fallbackAnswer(query, context);
            }
        }
        return fallbackAnswer(query, context);
    }

    private String buildPrompt(String query, EvidenceContext context) {
        return """
                你是企业知识库问答助手。
                只能基于给定证据回答问题。
                如果证据不足，请明确说明“知识库中没有足够信息”。
                回答时尽量引用证据编号，例如 [1][2]。
                不要编造证据中不存在的内容。

                证据：
                %s

                用户问题：
                %s

                请输出：
                结论：
                ...

                依据：
                ...

                补充说明：
                ...
                """.formatted(context.getContextText(), query == null ? "" : query);
    }

    private String fallbackAnswer(String query, EvidenceContext context) {
        StringBuilder answer = new StringBuilder();
        answer.append("结论：\n");
        answer.append("知识库中检索到与问题相关的证据，建议以这些证据为准继续分析。");
        if (!context.getCitations().isEmpty()) {
            answer.append(" [1]");
        }
        answer.append("\n\n依据：\n");
        context.getCitations().forEach(citation -> answer.append("- [")
                .append(citation.getIndex())
                .append("] ")
                .append(defaultText(citation.getFileName(), "unknown"))
                .append(" / ")
                .append(defaultText(citation.getHeadingPath(), "未命名章节"))
                .append("：")
                .append(defaultText(citation.getSnippet(), ""))
                .append("\n"));
        answer.append("\n补充说明：\n");
        answer.append("当前回答由规则化 fallback 生成；如果配置了 DashScope API Key，会优先由模型基于证据生成更自然的答案。");
        return answer.toString();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

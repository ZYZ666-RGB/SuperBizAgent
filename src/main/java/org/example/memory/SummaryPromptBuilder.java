package org.example.memory;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SummaryPromptBuilder {

    public String build(String oldSummary, List<ConversationMessage> messages) {
        StringBuilder builder = new StringBuilder();
        builder.append("请将下面的历史对话压缩成 sessionSummary，用于后续上下文工程调度。\n\n");
        builder.append("要求：\n");
        builder.append("1. 只保留当前 session 中对后续回答有帮助的信息；\n");
        builder.append("2. 只覆盖五类内容：用户目标、已确定方案、重要约束、已解决问题、未完成事项；\n");
        builder.append("3. 删除寒暄、重复、临时情绪、过程废话和无意义内容；\n");
        builder.append("4. 不要编造原文中没有的信息；\n");
        builder.append("5. 控制在 300 字以内，使用紧凑条目。\n\n");
        builder.append("旧摘要：\n");
        builder.append(isBlank(oldSummary) ? "无" : oldSummary).append("\n\n");
        builder.append("新增历史：\n");
        for (ConversationMessage message : messages) {
            builder.append(formatRole(message.getRole())).append(": ");
            builder.append(message.getContent()).append("\n");
        }
        return builder.toString();
    }

    private String formatRole(String role) {
        if ("user".equals(role)) {
            return "用户";
        }
        if ("assistant".equals(role)) {
            return "助手";
        }
        if ("tool".equals(role)) {
            return "工具";
        }
        return "系统";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

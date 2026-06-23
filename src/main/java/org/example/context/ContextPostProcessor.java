package org.example.context;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.example.memory.LongTermMemoryService;
import org.example.memory.SummaryMemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ContextPostProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ContextPostProcessor.class);

    private final SummaryMemoryService summaryMemoryService;
    private final LongTermMemoryService longTermMemoryService;

    public ContextPostProcessor(
            SummaryMemoryService summaryMemoryService,
            LongTermMemoryService longTermMemoryService) {
        this.summaryMemoryService = summaryMemoryService;
        this.longTermMemoryService = longTermMemoryService;
    }

    public void afterChat(
            String userId,
            String sessionId,
            String question,
            String answer,
            DashScopeChatModel chatModel) {
        summaryMemoryService.refreshSummaryIfNeeded(userId, sessionId, chatModel);
        if (!shouldWriteLongTermMemory(question, answer)) {
            logger.info("Skipped long-term memory extraction by context gate. userId={}, sessionId={}",
                    userId, sessionId);
            return;
        }
        longTermMemoryService.extractAndSaveAfterChat(userId, sessionId, question, answer, chatModel);
    }

    boolean shouldWriteLongTermMemory(String question, String answer) {
        String q = defaultText(question, "");
        String a = defaultText(answer, "");
        String combined = (q + "\n" + a).toLowerCase(Locale.ROOT);
        if (q.isBlank() || a.isBlank()) {
            return false;
        }
        if (containsAny(combined, "记住", "帮我记", "remember this", "please remember")) {
            return true;
        }
        if (containsAny(combined, "我喜欢", "我不喜欢", "我偏好", "我的偏好", "以后默认", "默认使用")) {
            return true;
        }
        boolean projectScope = containsAny(combined, "我的项目", "我们项目", "当前项目", "这个项目", "长期", "团队");
        boolean durableFact = containsAny(combined, "已确定", "决定", "约束", "必须", "不要", "方案", "架构", "接口", "待办", "未完成");
        if (projectScope && durableFact) {
            return true;
        }
        boolean userDirective = containsAny(q.toLowerCase(Locale.ROOT), "以后", "下次", "默认", "不要", "必须");
        boolean enoughSignal = containsAny(a.toLowerCase(Locale.ROOT), "结论", "约束", "后续", "下一步", "未完成事项");
        return userDirective && enoughSignal;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String defaultText(String value, String fallback) {
        return value == null ? fallback : value;
    }
}

package org.example.service;

import org.example.memory.MemoryPromptContext;
import org.example.memory.dto.ChatMessageDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatServiceMemoryPromptTest {

    @Test
    void memoryPromptContainsSummaryAndRecentMessages() {
        ChatService chatService = new ChatService();
        MemoryPromptContext context = new MemoryPromptContext();
        context.setSessionSummary("stable project summary");
        context.setRecentMessages(List.of(
                new ChatMessageDTO(1L, "user", "question from recent memory"),
                new ChatMessageDTO(2L, "assistant", "answer from recent memory")
        ));

        String prompt = chatService.buildSystemPrompt(context);

        assertThat(prompt).contains("SuperBizAgent");
        assertThat(prompt).contains("stable project summary");
        assertThat(prompt).contains("question from recent memory");
        assertThat(prompt).contains("answer from recent memory");
    }
}

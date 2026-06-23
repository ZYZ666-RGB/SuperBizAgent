package org.example.context;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPostProcessorTest {

    private final ContextPostProcessor postProcessor = new ContextPostProcessor(null, null);

    @Test
    void writesExplicitRememberAndDurableProjectFacts() {
        assertThat(postProcessor.shouldWriteLongTermMemory(
                "请记住我以后默认使用中文回答",
                "好的，之后会默认使用中文。"))
                .isTrue();

        assertThat(postProcessor.shouldWriteLongTermMemory(
                "这个项目的方案定了吗？",
                "结论：当前项目已确定采用统一 Context Engineering 层，这是重要约束。"))
                .isTrue();
    }

    @Test
    void skipsOrdinaryEphemeralTurns() {
        assertThat(postProcessor.shouldWriteLongTermMemory(
                "现在几点？",
                "现在是下午三点。"))
                .isFalse();
    }
}

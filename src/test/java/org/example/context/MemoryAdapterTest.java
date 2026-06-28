package org.example.context;

import org.example.memory.UserMemory;
import org.example.rag.chunk.TokenEstimator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryAdapterTest {

    @Test
    void chineseQueryUsesBigramsForRelevance() {
        MemoryAdapter adapter = new MemoryAdapter(
                null,
                new ContextConfig(),
                new ContextCompressor(new TokenEstimator()));

        double relevance = adapter.lexicalRelevance("当前项目上下文工程", "当前项目必须使用统一 Context Engineering 层。");

        assertThat(relevance).isGreaterThan(0.2);
    }

    @Test
    void identityQuestionKeepsNameMemoryRelevant() {
        MemoryAdapter adapter = new MemoryAdapter(
                null,
                new ContextConfig(),
                new ContextCompressor(new TokenEstimator()));
        UserMemory memory = new UserMemory();
        memory.setMemoryType("preference");
        memory.setContent("用户的名字是赵宇哲，以后应使用这个名字来称呼他。");

        double relevance = adapter.memoryRelevance("我是谁？", memory);

        assertThat(relevance).isGreaterThanOrEqualTo(1.0);
    }
}

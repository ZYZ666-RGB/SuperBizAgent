package org.example.context;

import org.example.memory.dto.ChatMessageDTO;
import org.example.rag.chunk.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContextBuilderTest {

    private final ContextConfig config = new ContextConfig();
    private final ContextCompressor compressor = new ContextCompressor(new TokenEstimator());
    private final ContextBuilder contextBuilder = new ContextBuilder(config, compressor);

    @Test
    void buildsFixedSectionsAndRanksPacketsByCompositeScore() {
        ContextPacket low = memoryPacket("old-low", "旧偏好", 0.2, 0.2, 0.2);
        ContextPacket high = memoryPacket("new-high", "当前项目必须使用 Milvus", 0.9, 0.8, 0.7);

        ConversationState state = new ConversationState();
        state.setSessionSummary("用户目标：增强上下文工程。");
        state.setRecentMessages(List.of(
                new ChatMessageDTO(1L, "user", "我们先讨论方案"),
                new ChatMessageDTO(2L, "assistant", "建议先做统一 Context 层")));

        ContextBuildRequest request = new ContextBuildRequest();
        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.setQuery("当前项目上下文工程");
        request.setRuntimeContext(runtimeContext);
        request.setTask("回答当前问题");

        ContextBuildResult result = contextBuilder.build(request, state, List.of(low, high));

        assertThat(result.getFinalContext())
                .contains("[Role & Policies]")
                .contains("[Task]")
                .contains("[Conversation Summary]")
                .contains("[Recent Conversation]")
                .contains("[Memory]")
                .contains("[Evidence]")
                .contains("[Tool Results]")
                .contains("[Agent State]")
                .contains("[Output]");
        assertThat(result.getSelectedPackets()).extracting(ContextPacket::getSourceId)
                .contains("new-high");
        assertThat(result.getFinalContext().indexOf("当前项目必须使用 Milvus"))
                .isLessThan(result.getFinalContext().indexOf("旧偏好"));
    }

    @Test
    void dropsMemoryBelowMinimumRelevance() {
        ContextPacket packet = memoryPacket("irrelevant", "完全无关内容", 0.05, 1.0, 1.0);
        ContextBuildRequest request = new ContextBuildRequest();
        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.setQuery("CPU 告警");
        request.setRuntimeContext(runtimeContext);

        ContextBuildResult result = contextBuilder.build(request, new ConversationState(), List.of(packet));

        assertThat(result.getSelectedPackets()).isEmpty();
        assertThat(result.getDroppedPackets()).extracting(ContextPacket::getSourceId)
                .contains("irrelevant");
    }

    private ContextPacket memoryPacket(
            String sourceId,
            String content,
            double relevance,
            double recency,
            double importance) {
        ContextPacket packet = new ContextPacket();
        packet.setType(ContextSourceType.MEMORY);
        packet.setSourceId(sourceId);
        packet.setTitle(sourceId);
        packet.setContent(content);
        packet.setRelevanceScore(relevance);
        packet.setRecencyScore(recency);
        packet.setImportanceScore(importance);
        return packet;
    }
}

package org.example.context;

import org.example.memory.ConversationMemoryService;
import org.example.memory.SummaryMemoryService;
import org.example.rag.chunk.TokenEstimator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContextEngineeringServiceTest {

    @Test
    void finalExamQuestionLoadsRagEvidence() {
        ConversationMemoryService conversationMemoryService = mock(ConversationMemoryService.class);
        SummaryMemoryService summaryMemoryService = mock(SummaryMemoryService.class);
        MemoryAdapter memoryAdapter = mock(MemoryAdapter.class);
        RagEvidenceAdapter ragEvidenceAdapter = mock(RagEvidenceAdapter.class);
        ContextConfig config = new ContextConfig();
        ContextCompressor compressor = new ContextCompressor(new TokenEstimator());

        when(summaryMemoryService.getSummary(anyString(), anyString())).thenReturn(Optional.empty());
        when(conversationMemoryService.getRecentMessages(anyString(), anyString(), anyInt())).thenReturn(List.of());
        when(conversationMemoryService.countMessages(anyString(), anyString())).thenReturn(0L);
        when(memoryAdapter.fetchRelevantMemories(any())).thenReturn(List.of());

        ContextPacket evidence = new ContextPacket();
        evidence.setType(ContextSourceType.RAG_EVIDENCE);
        evidence.setSourceId("chunk-1");
        evidence.setTitle("期末复习内容.docx");
        evidence.setContent("期末需要复习数据库事务、索引和 SQL 查询优化。");
        evidence.setRelevanceScore(0.8);
        evidence.setRecencyScore(0.6);
        evidence.setImportanceScore(0.7);
        when(ragEvidenceAdapter.fetchEvidence(any())).thenReturn(List.of(evidence));

        ContextEngineeringService service = new ContextEngineeringService(
                conversationMemoryService,
                summaryMemoryService,
                memoryAdapter,
                ragEvidenceAdapter,
                new ContextBuilder(config, compressor),
                config);

        ContextBuildResult result = service.buildForChat("user-a", "session-a", "我期末需要复习什么？");

        assertThat(result.getSelectedPackets())
                .anySatisfy(packet -> assertThat(packet.getType()).isEqualTo(ContextSourceType.RAG_EVIDENCE));
        assertThat(result.getFinalContext()).contains("期末需要复习数据库事务");
    }
}

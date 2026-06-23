package org.example.context;

import org.example.rag.chunk.TokenEstimator;
import org.example.rag.online.AdvancedRagOnlineService;
import org.example.rag.online.model.RagQueryResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagEvidenceAdapterTest {

    @Test
    void convertsRerankedCandidatesToEvidencePackets() {
        AdvancedRagOnlineService ragOnlineService = mock(AdvancedRagOnlineService.class);
        RagQueryResult result = new RagQueryResult();
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId("chunk-1");
        candidate.setDocumentId("doc-1");
        candidate.setSourcePath("runbook.md");
        candidate.setHeadingPath("CPU 排查");
        candidate.setChunkIndex(3);
        candidate.setContent("CPU 使用率过高时先检查热点进程。");
        candidate.setRerankScore(0.86);
        result.setCandidates(List.of(candidate));
        when(ragOnlineService.search(any())).thenReturn(result);

        RagEvidenceAdapter adapter = new RagEvidenceAdapter(
                ragOnlineService,
                new ContextConfig(),
                new ContextCompressor(new TokenEstimator()));
        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.setQuery("CPU 使用率过高如何排查");
        runtimeContext.setNamespace("default");

        List<ContextPacket> packets = adapter.fetchEvidence(runtimeContext);

        assertThat(packets).hasSize(1);
        assertThat(packets.get(0).getType()).isEqualTo(ContextSourceType.RAG_EVIDENCE);
        assertThat(packets.get(0).getContent())
                .contains("<evidence source=\"runbook.md\" title=\"CPU 排查\" chunk=\"3\" score=\"0.860\">")
                .contains("CPU 使用率过高时先检查热点进程。")
                .contains("</evidence>");
        assertThat(packets.get(0).getMetadata())
                .containsEntry("docId", "doc-1")
                .containsEntry("source", "runbook.md")
                .containsEntry("title", "CPU 排查")
                .containsEntry("chunkIndex", 3);
    }
}

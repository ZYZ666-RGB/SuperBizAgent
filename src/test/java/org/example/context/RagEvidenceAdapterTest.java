package org.example.context;

import org.example.rag.chunk.TokenEstimator;
import org.example.rag.index.RagMetadataStoreService;
import org.example.rag.model.IndexStatus;
import org.example.rag.model.RagChunk;
import org.example.rag.model.RagDocument;
import org.example.rag.online.AdvancedRagOnlineService;
import org.example.rag.online.model.RagQueryResult;
import org.example.rag.online.model.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
        RagMetadataStoreService metadataStoreService = mock(RagMetadataStoreService.class);

        RagEvidenceAdapter adapter = new RagEvidenceAdapter(
                ragOnlineService,
                metadataStoreService,
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

    @Test
    void fallsBackToLatestUploadedDocumentForGenericUploadQuestion() {
        AdvancedRagOnlineService ragOnlineService = mock(AdvancedRagOnlineService.class);
        RagQueryResult emptyResult = new RagQueryResult();
        emptyResult.setCandidates(List.of());
        when(ragOnlineService.search(any())).thenReturn(emptyResult);

        RagDocument document = new RagDocument();
        document.setDocumentId("doc-latest");
        document.setNamespace("default");
        document.setFileName("期末复习内容.docx");
        document.setFileType("docx");
        document.setStatus(IndexStatus.COMPLETED);

        RagChunk chunk = new RagChunk();
        chunk.setChunkId("chunk-1");
        chunk.setDocumentId("doc-latest");
        chunk.setNamespace("default");
        chunk.setFileName("期末复习内容.docx");
        chunk.setHeadingPath("复习重点");
        chunk.setChunkIndex(0);
        chunk.setContent("期末需要复习数据库事务、索引、范式和 SQL 查询优化。");

        RagMetadataStoreService metadataStoreService = mock(RagMetadataStoreService.class);
        when(metadataStoreService.findLatestCompletedDocument("default")).thenReturn(Optional.of(document));
        when(metadataStoreService.findInitialChildChunks(eq("doc-latest"), anyInt())).thenReturn(List.of(chunk));

        RagEvidenceAdapter adapter = new RagEvidenceAdapter(
                ragOnlineService,
                metadataStoreService,
                new ContextConfig(),
                new ContextCompressor(new TokenEstimator()));
        RuntimeContext runtimeContext = new RuntimeContext();
        runtimeContext.setQuery("你看一下这个上传的内容");
        runtimeContext.setNamespace("default");

        List<ContextPacket> packets = adapter.fetchEvidence(runtimeContext);

        assertThat(packets).hasSize(1);
        assertThat(packets.get(0).getContent())
                .contains("期末复习内容.docx")
                .contains("期末需要复习数据库事务");
        assertThat(packets.get(0).getMetadata())
                .containsEntry("matchedBy", List.of("latest-upload-fallback"));
    }
}

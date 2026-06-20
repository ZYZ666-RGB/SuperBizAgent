package org.example.rag.online.retrieve;

import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.RetrievalCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RrfFusionServiceTest {

    @Test
    void fusesDenseAndSparseRankingsByChunkId() {
        RrfFusionService service = new RrfFusionService(new RagOnlineProperties(), new RetrievalDedupService());

        List<RetrievalCandidate> fused = service.fuse(List.of(
                List.of(candidate("chunk-a", "dense"), candidate("chunk-b", "dense")),
                List.of(candidate("chunk-b", "sparse"), candidate("chunk-c", "sparse"))
        ), 3);

        assertThat(fused).hasSize(3);
        assertThat(fused.get(0).getChunkId()).isEqualTo("chunk-b");
        assertThat(fused.get(0).getMatchedBy()).contains("dense", "sparse");
        assertThat(fused).extracting(RetrievalCandidate::getFusedScore).allMatch(score -> score > 0.0);
    }

    private RetrievalCandidate candidate(String chunkId, String source) {
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId(chunkId);
        candidate.setContent("content " + chunkId);
        candidate.getMatchedBy().add(source);
        return candidate;
    }
}

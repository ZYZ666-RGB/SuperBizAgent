package org.example.rag.online.generate;

import org.example.rag.online.config.RagOnlineProperties;
import org.example.rag.online.model.EvidenceContext;
import org.example.rag.online.model.RetrievalCandidate;
import org.example.rag.online.model.VerificationResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerVerifierServiceTest {

    private final AnswerVerifierService service = new AnswerVerifierService(new RagOnlineProperties());

    @Test
    void marksAnswerWithEvidenceAndCitationAsSupported() {
        EvidenceContext context = new EvidenceContext();
        RetrievalCandidate candidate = new RetrievalCandidate();
        candidate.setChunkId("chunk-1");
        candidate.setFusedScore(0.04);
        context.setUsedChunks(List.of(candidate));

        VerificationResult result = service.verify("Redis 超时", "结论来自证据 [1]", context);

        assertThat(result.getSupported()).isTrue();
        assertThat(result.getConfidence()).isGreaterThanOrEqualTo(0.55);
    }

    @Test
    void rejectsAnswerWithoutEvidence() {
        VerificationResult result = service.verify("unknown", "随便回答", new EvidenceContext());

        assertThat(result.getSupported()).isFalse();
        assertThat(result.getConfidence()).isZero();
    }
}

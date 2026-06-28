package org.example.rag.eval;

import org.example.rag.online.model.RagAnswer;
import org.example.rag.online.model.RagQueryRequest;
import org.example.rag.online.model.RetrievalCandidate;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class RagEvalRunner {

    public RagEvalReport run(List<RagEvalCase> cases, Function<RagQueryRequest, RagAnswer> answerer) {
        List<RagEvalResult> results = cases.stream()
                .map(evalCase -> evaluate(evalCase, answerer.apply(requestFor(evalCase))))
                .toList();
        return new RagEvalReport(results);
    }

    private RagQueryRequest requestFor(RagEvalCase evalCase) {
        RagQueryRequest request = new RagQueryRequest();
        request.setQuery(evalCase.getQuery());
        request.setNamespace(defaultText(evalCase.getNamespace(), "default"));
        request.setTopK(evalCase.getTopK() == null ? 3 : evalCase.getTopK());
        request.setDebug(true);
        return request;
    }

    private RagEvalResult evaluate(RagEvalCase evalCase, RagAnswer answer) {
        RagEvalResult result = new RagEvalResult();
        result.setCaseId(evalCase.getId());
        result.setShouldAnswer(evalCase.isShouldAnswer());
        result.setSupported(answer != null && Boolean.TRUE.equals(answer.getSupported()));
        result.setConfidence(answer == null || answer.getConfidence() == null ? 0.0 : answer.getConfidence());
        result.setUsedChunkIds(usedChunkIds(answer));
        result.setRetrievalPassed(retrievalPassed(evalCase, result.getUsedChunkIds()));
        result.setCitationPassed(!evalCase.isShouldAnswer()
                || (answer != null && answer.getCitations() != null && !answer.getCitations().isEmpty()));
        result.setRequiredTermsPassed(requiredTermsPassed(evalCase, answer));
        result.setConfidencePassed(!evalCase.isShouldAnswer() || result.getConfidence() >= evalCase.getMinConfidence());
        result.setRefusalPassed(!evalCase.isShouldAnswer() && !result.isSupported());
        result.setMessage(answer == null ? "answer was null" : answer.getMessage());
        return result;
    }

    private List<String> usedChunkIds(RagAnswer answer) {
        if (answer == null || answer.getUsedChunks() == null) {
            return List.of();
        }
        return answer.getUsedChunks().stream()
                .map(RetrievalCandidate::getChunkId)
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }

    private boolean retrievalPassed(RagEvalCase evalCase, List<String> usedChunkIds) {
        if (!evalCase.isShouldAnswer()) {
            return true;
        }
        if (evalCase.getExpectedChunkIds().isEmpty()) {
            return true;
        }
        return usedChunkIds.containsAll(evalCase.getExpectedChunkIds());
    }

    private boolean requiredTermsPassed(RagEvalCase evalCase, RagAnswer answer) {
        if (!evalCase.isShouldAnswer() || evalCase.getRequiredAnswerTerms().isEmpty()) {
            return true;
        }
        String text = answer == null || answer.getAnswer() == null
                ? ""
                : answer.getAnswer().toLowerCase(Locale.ROOT);
        return evalCase.getRequiredAnswerTerms().stream()
                .map(term -> term.toLowerCase(Locale.ROOT))
                .allMatch(text::contains);
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

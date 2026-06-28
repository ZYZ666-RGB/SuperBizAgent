package org.example.rag.eval;

import java.util.List;
import java.util.Locale;

public class RagEvalReport {

    private final List<RagEvalResult> results;

    public RagEvalReport(List<RagEvalResult> results) {
        this.results = results == null ? List.of() : results;
    }

    public List<RagEvalResult> getResults() {
        return results;
    }

    public List<RagEvalResult> getFailedResults() {
        return results.stream()
                .filter(result -> !result.passed())
                .toList();
    }

    public int total() {
        return results.size();
    }

    public int answerableCount() {
        return (int) results.stream()
                .filter(RagEvalResult::isShouldAnswer)
                .count();
    }

    public int refusalCount() {
        return total() - answerableCount();
    }

    public double retrievalRecall() {
        return ratio(results.stream()
                        .filter(RagEvalResult::isShouldAnswer)
                        .filter(RagEvalResult::isRetrievalPassed)
                        .count(),
                answerableCount());
    }

    public double citationRate() {
        return ratio(results.stream()
                        .filter(RagEvalResult::isShouldAnswer)
                        .filter(RagEvalResult::isCitationPassed)
                        .count(),
                answerableCount());
    }

    public double answerSupportRate() {
        return ratio(results.stream()
                        .filter(RagEvalResult::isShouldAnswer)
                        .filter(RagEvalResult::isSupported)
                        .filter(RagEvalResult::isConfidencePassed)
                        .count(),
                answerableCount());
    }

    public double requiredTermRate() {
        return ratio(results.stream()
                        .filter(RagEvalResult::isShouldAnswer)
                        .filter(RagEvalResult::isRequiredTermsPassed)
                        .count(),
                answerableCount());
    }

    public double refusalAccuracy() {
        return ratio(results.stream()
                        .filter(result -> !result.isShouldAnswer())
                        .filter(RagEvalResult::isRefusalPassed)
                        .count(),
                refusalCount());
    }

    public String formatSummary() {
        return String.format(Locale.ROOT,
                "RAG eval: total=%d, retrievalRecall=%.3f, citationRate=%.3f, "
                        + "answerSupportRate=%.3f, requiredTermRate=%.3f, refusalAccuracy=%.3f, failures=%d",
                total(),
                retrievalRecall(),
                citationRate(),
                answerSupportRate(),
                requiredTermRate(),
                refusalAccuracy(),
                getFailedResults().size());
    }

    private double ratio(long numerator, long denominator) {
        if (denominator == 0) {
            return 1.0;
        }
        return (double) numerator / (double) denominator;
    }
}

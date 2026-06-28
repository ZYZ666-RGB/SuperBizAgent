# RAG Evaluation

This project now has a small offline evaluation gate for the advanced RAG online
pipeline. It is intentionally deterministic: retrieval candidates are controlled
inside the test, while rerank fallback, context building, citation creation,
answer fallback, and answer verification use the real project code.

## Command

```bash
mvn -q -Dtest=RagEvalDatasetTest test
```

The full test suite also runs the eval gate:

```bash
mvn -q test
```

## Dataset

Evaluation cases live in:

```text
src/test/resources/rag/eval-cases.json
```

Each case declares:

- `query`: user question.
- `shouldAnswer`: whether the system should answer or refuse.
- `expectedChunkIds`: chunks that must appear in used evidence.
- `requiredAnswerTerms`: terms that must appear in the final answer.
- `minConfidence`: minimum verifier confidence for answerable cases.

## Metrics

The gate tracks:

- `retrievalRecall`: expected chunks appeared in `usedChunks`.
- `citationRate`: answerable cases returned citations.
- `answerSupportRate`: answerable cases were marked supported above confidence threshold.
- `requiredTermRate`: final answer contained required terms.
- `refusalAccuracy`: unanswerable cases were refused.

The current seed dataset covers representative runbook scenarios for Redis
timeouts, high CPU, high memory/OOM, high disk usage, service unavailable, slow
response, and no-evidence refusal. Add production questions, expected chunks,
and unanswerable cases before using the score as a release signal.

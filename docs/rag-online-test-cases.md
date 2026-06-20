# RAG Online Pipeline Test Cases

This document records the retained test coverage for the Advanced RAG online pipeline.

## Verification Command

```bash
mvn -q test
```

Latest local verification on this branch: passed.

## Covered Cases

- `QueryAnalysisServiceTest`
  - Classifies exact-term/troubleshooting-like queries with `traceId`, service names, and Redis metadata.
  - Classifies procedure questions and extracts component metadata.

- `QueryRewriteServiceTest`
  - Keeps the original user query.
  - Adds troubleshooting-oriented query variants for better recall.

- `HyDEGeneratorServiceTest`
  - Generates a hypothetical troubleshooting text for retrieval only.

- `RrfFusionServiceTest`
  - Fuses dense and sparse ranked lists by `chunkId`.
  - Merges `matchedBy` sources and creates positive RRF scores.

- `SparseRetrieverServiceTest`
  - Searches persisted offline `rag_chunk` metadata.
  - Respects `namespace` and metadata filters.
  - Excludes parent chunks from direct sparse retrieval.

- `ContextBuilderServiceTest`
  - Builds numbered evidence blocks.
  - Preserves citations.
  - Expands short child chunks from parent chunks when available.

- `AnswerVerifierServiceTest`
  - Marks cited evidence-backed answers as supported.
  - Rejects answers when no evidence is available.

- `AdvancedRagOnlineServiceTest`
  - Returns answer, citations, confidence, and trace when evidence exists.
  - Refuses to answer when retrieval returns no evidence.

## Expected Effect

The online chain can now be tested without requiring live Milvus or DashScope services for every case. Dense retrieval remains wired to Milvus for runtime use, while local tests keep the core reasoning path stable: analysis, rewrite, sparse recall, fusion, rerank fallback, context/citation construction, answer verification, and no-evidence refusal.

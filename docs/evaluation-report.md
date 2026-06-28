# Evaluation Report

SuperBizAgent now exposes a runtime evaluation report page:

```text
http://localhost:9900/eval-report.html
```

The page calls these APIs:

```text
GET  /api/eval/report
POST /api/eval/run
```

`GET /api/eval/report` returns the latest generated report from:

```text
target/eval-report.json
```

If no live report has been generated yet, it returns a read-only snapshot.

## What It Checks

- LLM model probes: calls each configured DashScope model and scores a small
  operations prompt by concept coverage, answer length, and obvious unsafe
  wording.
- RAG live check: optionally seeds a small runbook into the `eval_report`
  namespace, then checks answerable retrieval, citations, confidence, support,
  and no-evidence refusal.
- Memory live check: writes a unique identity memory for `eval-report-user`,
  verifies same-user retrieval, checks that `eval-report-other-user` cannot see
  it, and confirms that the memory is selected into the final chat context for
  an identity question.
- Offline gate: reads the latest Surefire result for `RagEvalDatasetTest`.

## Configuration

Default settings are in `src/main/resources/application.yml`:

```yaml
evaluation:
  namespace: eval_report
  report-path: target/eval-report.json
  seed-rag-demo: true
  llm:
    models:
      - deepseek-v4-flash
      - qwen-plus
      - qwen-turbo
```

The model list can also be overridden from the report page before clicking
"Run Evaluation".

## How To Read It

- `PASS`: current smoke evaluation meets the configured threshold.
- `WARN`: usable but needs review.
- `FAIL`: the feature is not passing the current check.
- `SKIPPED`: the check did not run, usually because configuration or a previous
  test report is missing.

For real product readiness, keep adding production questions and documents.
This report is a practical smoke gate, not a complete benchmark suite.

## When Features Are Actually Effective

Long-term memory is effective only after all of these are true:

- The request carries a stable user id through `X-User-Id` or `UserId`.
- A memory is admitted into `user_memory` and remains `enabled = 1`.
- The current query retrieves that memory by vector or MySQL fallback.
- Context engineering selects the memory into the final prompt instead of
  dropping it by relevance or token budget.

RAG is effective only after all of these are true:

- Documents are indexed into the namespace used by the runtime query.
- The online retriever returns non-empty `usedChunks`.
- The answer includes citations and passes the support/confidence check.
- For chat usage, the query must look like a document, runbook, troubleshooting,
  configuration, incident, log, alert, or service question so the chat context
  layer loads RAG evidence.

# CLS Real Query

`QueryLogsTools` supports two modes:

- `cls.mock-enabled: true`: use local mock log data for demos and tests.
- `cls.mock-enabled: false`: call Tencent Cloud CLS `SearchLog`.

## Required Configuration

Set Tencent Cloud credentials and topic mappings through environment variables
or Spring configuration:

```bash
TENCENT_CLOUD_SECRET_ID=...
TENCENT_CLOUD_SECRET_KEY=...
TENCENT_CLS_REGION=ap-guangzhou
CLS_TOPIC_SYSTEM_METRICS=...
CLS_TOPIC_APPLICATION_LOGS=...
CLS_TOPIC_DATABASE_SLOW_QUERY=...
CLS_TOPIC_SYSTEM_EVENTS=...
```

Temporary credentials can also set:

```bash
TENCENT_CLOUD_TOKEN=...
```

## Topic Names

The tool keeps the friendly topic names used by the AIOps prompts:

- `system-metrics`
- `application-logs`
- `database-slow-query`
- `system-events`

In real mode, each friendly name must be mapped to a real CLS `TopicId`.
Alternatively, callers can pass an actual `TopicId` as `logTopic`.

## Query Window

The default real query window is the last 30 minutes:

```yaml
cls:
  query-window-minutes: 30
  timeout: 10
```

`SearchLog` uses millisecond timestamps and returns up to the requested limit.
The tool currently caps the user-facing limit at 100 records.

---
name: observability-and-instrumentation
description: Use when the user asks to add or review logging, metrics, tracing, alerting, or production diagnostic signals, or when an authorized fix specifically requires missing evidence. In this Teamobi NRO repository, do not trigger for every feature and do not add a telemetry dependency without explicit approval; NRO-specific skills remain workflow owners.
---

# Observability and Instrumentation

## Overview

Code you can't observe is code you can't operate. Observability is the ability to answer "what is the system doing and why?" from the outside, using the telemetry the code emits. Instrumentation is not a post-launch add-on — it's written alongside the feature, the same way tests are. If a feature ships without telemetry, the first user-reported bug becomes archaeology instead of a query.

## Teamobi NRO Repository Adaptation

- Read root `AGENTS.md`, the project-local `security-and-hardening` skill, and the applicable NRO-specific skill first.
- Prefer existing Java logging, counters, admin diagnostics, protocol probes, and operational scripts. This skill does not authorize adding OpenTelemetry, Prometheus, a logging backend, an agent, or any other dependency/service.
- Model the relevant boundaries as game command/session, JDBC/MySQL, scheduler/background task, filesystem/PowerShell, HTA administration, client-data generation, and external service—not automatically as HTTP endpoints.
- Never log credentials, tokens, raw packet bodies, full player/account records, or unredacted PII. Prefer a short-lived non-secret operation/session correlation value when correlation is genuinely needed; do not use account identity as a metric label.
- Adding instrumentation is a code change and requires implementation authority. A diagnosis-only request must use existing logs and safe probes.
- Do not restart services, mutate databases, deploy `20.jar`, or induce a production failure merely to verify telemetry. Follow the build, protocol, cache, and A -> B -> A relogin gates in root `AGENTS.md` when the owning workflow requires them.

## When to Use

- Adding or changing logging, metrics, tracing, alerting, or operational diagnostics
- Adding a high-risk command handler, background job, database path, or external integration when observability is explicitly in scope
- A production incident took too long to diagnose ("we couldn't tell what happened")
- Setting up or reviewing alerting rules
- Reviewing a PR that adds I/O, retries, queues, or cross-service calls

**NOT for:**
- Diagnosing a failure happening right now — use the project-local `systematic-debugging` skill first; add instrumentation only if the authorized fix needs it
- Profiling measured slowness without a request to add durable signals — measure through the owning NRO workflow first
- Deployment and rollback decisions — root `AGENTS.md` and `BUILD_JAVA_STANDARD.md` own those gates

## Process

### 1. Define "working" before instrumenting

Telemetry without a question is noise. Before adding any instrumentation, write down 2–4 questions an on-call engineer will ask about this feature:

```
FEATURE: checkout payment retry
QUESTIONS ON-CALL WILL ASK:
1. What fraction of payments succeed on first attempt vs after retry?
2. When a payment fails permanently, why? (provider error? timeout? validation?)
3. Is the payment provider slower than usual?
→ Every signal below must help answer one of these.
```

If you can't name the questions, you're not ready to instrument — you'll log everything and learn nothing.

### 2. Pick the right signal for each question

| Signal | Answers | Cost profile | Example |
|---|---|---|---|
| **Structured log** | "What happened in this specific case?" | Per-event; grows with traffic | `payment_failed` with provider error code |
| **Metric** | "How often / how fast, in aggregate?" | Fixed per series; cheap to query | p99 latency of provider calls |
| **Trace** | "Where did time go across services?" | Per-request; usually sampled | One slow checkout, broken down by hop |

Rule of thumb: metrics tell you **that** something is wrong, traces tell you **where**, logs tell you **why**.

### 3. Structured logging

Log events, not prose. Use the repository's existing Java logging style and stable event names with allowlisted key/value fields where supported. JSON is useful only when the configured sink consumes it; do not add a logging dependency solely to change format. The TypeScript example below illustrates the shape, not the required stack:

```typescript
// BAD: string interpolation — unqueryable, inconsistent
logger.info(`Payment ${id} failed for user ${userId} after ${n} retries`);

// GOOD: stable event name + structured fields
logger.warn({
  event: 'payment_failed',
  paymentId: id,
  provider: 'stripe',
  errorCode: err.code,
  attempt: n,
}, 'payment failed');
```

**Log levels — use them consistently:**

| Level | Meaning | On-call action |
|---|---|---|
| `error` | Invariant broken; someone may need to act | Investigate |
| `warn` | Degraded but handled (retry succeeded, fallback used) | Watch for trends |
| `info` | Significant business event (order placed, job finished) | None |
| `debug` | Diagnostic detail | Off in production by default |

**Correlation is required for multi-stage or asynchronous flows that cannot otherwise be reconstructed.** Generate a non-secret operation/session ID at the trusted system boundary and attach it to relevant log events and outbound work. Do not reuse account/player identity as the correlation ID. Simple single-path events do not need synthetic correlation fields:

```typescript
// Express: child logger per request, ID propagated downstream
app.use((req, res, next) => {
  req.id = req.headers['x-request-id'] ?? crypto.randomUUID();
  req.log = logger.child({ requestId: req.id });
  res.setHeader('x-request-id', req.id);
  next();
});
```

**When several entry points write to one log, name the entry point.** A correlation ID identifies a run; it does not say which code path started it. The same job reached by a scheduler, by a replay endpoint, and by a manual CLI run produces interchangeable lines in one sink, so attributing a line falls back to elimination — cross-reading the scheduler's history, the process table, a deploy log — and that argument holds only as long as those external records happen to still exist. Stamp the entry point where the run starts, next to the correlation ID, and propagate both the same way:

```typescript
// One helper for every entry point: the run's own logger carries both fields.
// `entryPoint`, not `source` — ECS reserves `source.*` for network fields.
export const runLog = (entryPoint: 'scheduler' | 'replay_endpoint' | 'cli', runId: string) =>
  logger.child({ entryPoint, requestId: runId });

// scheduler tick        -> runLog('scheduler', crypto.randomUUID())
// POST /jobs/:id/replay -> runLog('replay_endpoint', req.id)
// CLI invocation        -> runLog('cli', process.env.RUN_ID ?? crypto.randomUUID())
```

Both fields have to cross the same boundaries as the correlation ID — queue metadata, HTTP headers — or a worker re-derives the entry point and guesses. A field that merely correlates with an entry point is a hint, not an attribution: anything that can invoke the job can reproduce it.

**Never log secrets, tokens, passwords, or full PII.** This is a hard rule from the `security-and-hardening` skill — telemetry pipelines are a classic data-leak path. Allowlist fields; don't log whole request bodies.

### 4. Metrics

For command- or request-driven paths selected for instrumentation, apply **RED** to the relevant command handler and external dependency: **R**ate, **E**rrors, **D**uration. For resources such as queues, thread pools, JDBC pools, and hosts, use **USE**: **U**tilization, **S**aturation, **E**rrors. Instrument only signals that answer the stated operational questions.

As with tracing, the vendor-neutral path is the OpenTelemetry metrics API (same SDK and context as step 5). The example below uses Prometheus' `prom-client` — one common backend choice, not the only one; the RED/USE and cardinality rules are identical either way.

```typescript
import { Histogram } from 'prom-client';

const httpDuration = new Histogram({
  name: 'http_request_duration_seconds',
  help: 'HTTP request duration',
  labelNames: ['method', 'route', 'status_class'],  // '2xx', not '200'
  buckets: [0.05, 0.1, 0.25, 0.5, 1, 2.5, 5],
});
```

**Cardinality is the failure mode.** Every unique label combination is a separate time series. Labels must come from small, fixed sets (route template, status class, provider name). Never use user IDs, raw URLs, error messages, or other unbounded values as labels — that belongs in logs and traces.

```
OK as label:    route="/api/tasks/:id"   status_class="5xx"   provider="stripe"
NEVER a label:  user_id, email, request_id, full URL, error message text
```

Track averages never, percentiles always: an average hides the 1% of users having a terrible time. Use histograms and read p50/p95/p99.

### 5. Distributed tracing

When distributed tracing is explicitly requested and an approved backend/dependency boundary exists, prefer a vendor-neutral standard such as OpenTelemetry. Do not install it automatically. Auto-instrumentation examples for Node/HTTP do not apply to this Java TCP server without a separate compatibility and overhead review:

```typescript
// tracing.ts — must be imported before anything else
import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';

const sdk = new NodeSDK({
  serviceName: 'checkout-service',
  instrumentations: [getNodeAutoInstrumentations()],
});
sdk.start();
```

Add manual spans only around meaningful internal units of work (e.g., `applyDiscounts`, `chargeProvider`) and attach the attributes on-call will filter by. Propagate context across every async boundary — HTTP headers, queue message metadata — or the trace dies at the gap. Sample head-based at a low rate by default; keep 100% of errors if your backend supports tail sampling.

### 6. Alerting

Alert on **symptoms users feel**, not on causes:

```
SYMPTOM (page-worthy):           CAUSE (dashboard, not a page):
error rate > 1% for 5 min        CPU at 85%
p99 latency > 2s                 one pod restarted
queue age > 10 min               disk at 70%
```

Cause-based alerts fire when nothing is wrong and miss failures you didn't predict. Symptom-based alerts fire exactly when users are hurt, regardless of the cause.

Rules for every alert you create:

1. **It must be actionable.** If the response is "ignore it, it self-heals", delete the alert.
2. **It links to a runbook** — even three lines: what it means, first query to run, escalation path.
3. **It has a threshold and duration** justified by the SLO or by historical data, not by a guess.
4. Use two severities only: **page** (user-facing, act now) and **ticket** (degradation, act this week). A third tier becomes noise that trains people to ignore everything.

### 7. Verify the telemetry itself

Instrumentation is code; it can be wrong. Before calling the work done, trigger the paths and look at the actual output:

- In a safe local/test environment, trigger a controlled failure → locate it by the chosen correlation field and confirm only allowlisted fields are emitted
- Send controlled test traffic → confirm configured metric series appear with bounded labels and sane values
- If tracing is in scope, follow one operation across its instrumented boundaries → no broken spans
- If alerting is in scope, test-fire each new alert through the approved test path → confirm it reaches the intended channel and its runbook link works

## Common Rationalizations

| Rationalization | Reality |
|---|---|
| "I'll add logging after it works" | "After" becomes "after the first incident", which is the most expensive moment to discover you're blind. Instrument as you build. |
| "More logs = more observability" | Unstructured noise makes incidents slower, not faster. Three queryable events beat three hundred prose lines. |
| "console.log is fine for now" | Unstructured output can't be filtered, correlated, or alerted on. The structured logger costs five extra minutes once. |
| "We can just look at the dashboards when something breaks" | Dashboards built without defined questions show you everything except the answer. Start from on-call questions. |
| "Alert on everything important, we'll tune later" | A noisy pager trains people to ignore it. The tuning never happens; the missed real page does. |
| "User ID as a metric label makes debugging easier" | It also makes your metrics backend fall over. High-cardinality lookups belong in logs and traces. |
| "Tracing is overkill for our two services" | Two services already means cross-service latency questions logs can't answer. Auto-instrumentation makes the cost trivial. |

## Red Flags

- An observability-scoped change to retries, queues, or external calls with no signal answering the stated operational questions
- Important events emitted as inconsistent prose when the existing logger supports stable fields
- A multi-stage flow that cannot be reconstructed because it has no safe correlation/operation ID
- One log stream fed by a scheduler, a webhook, and manual runs, with no field naming which one produced the line
- Metrics labeled with user IDs, raw URLs, or error message text (cardinality bomb)
- Latency tracked as an average with no percentiles
- Alerts that fire daily and get acknowledged without action
- Alerts on causes (CPU, memory) paging humans while user-facing error rate is unmonitored
- Secrets, tokens, or full request bodies appearing in logs
- "It works on my machine" as the only evidence a production feature is healthy

## Verification

After instrumenting a feature, confirm:

- [ ] The on-call questions for this feature are written down, and each signal maps to one
- [ ] Relevant log events use the existing sink's queryable format, stable event names, allowlisted fields, and correlation only where the flow needs it
- [ ] Every shared log sink carries an entry-point field for ambiguous multi-entry flows, set where the run starts and propagated with its operation ID rather than inferred downstream
- [ ] No secrets, tokens, or unredacted PII in any log line (spot-check actual output)
- [ ] Requested RED/USE metrics cover the selected command/dependency/resource boundaries with bounded label sets
- [ ] When latency metrics are requested, a histogram exposes useful percentiles rather than only an average
- [ ] When tracing is requested, a single operation can be followed across instrumented boundaries without broken spans
- [ ] When alerting is requested, each new alert is symptom-based, has a runbook link, and was test-fired through an approved path
- [ ] A controlled failure in a safe environment was located using the new signals without exposing secrets or PII

For repository-specific build, runtime, protocol, cache, and deployment evidence, see root `AGENTS.md`, `BUILD_JAVA_STANDARD.md`, and the applicable NRO-specific skill.

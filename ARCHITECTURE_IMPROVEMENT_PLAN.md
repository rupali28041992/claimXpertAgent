# ClaimXpert — Architecture & Performance Improvement Plan

**Date:** 2026-08-25  
**Application:** `claims-assistant-backend` (Spring Boot 3.3.2 / Java 21 / MongoDB / Ollama)  
**Prepared by:** Architecture Review

---

## Executive Summary

The application has a solid multi-agent RAG pipeline foundation but has critical correctness gaps and a fully synchronous processing model that will block under any real load. This plan addresses those issues in a prioritised sequence — bugs first, then performance, then long-term architecture — so each phase delivers independent value without requiring the next one.

---

## Current State Snapshot

| Area | Status |
|------|--------|
| Claim persistence | **Broken** — orchestrator never saves to MongoDB |
| Submit endpoint | **Blocking** — blocks Tomcat thread for 15–60 sec (Ollama call) |
| Document processing | **Sequential** — files processed one at a time |
| Embedding cache | **None** — same query re-embedded on every request |
| OCR text safeguard | **Unenforced** — `maxOcrCharsPerDoc` config exists but is not applied |
| Vector search | In-memory cosine (acceptable for 22 clauses, will not scale) |
| Ollama timeout | **Not configured** — hung model blocks thread indefinitely |
| Correlation logging | **Missing** — `claimId` not threaded through MDC |
| Retry / dead-letter | **None** — Ollama failure silently downgrades to MANUAL_REVIEW |

---

## Phase 0 — Critical Bug Fixes (Do Immediately)

These are correctness issues that must be fixed before any other work. They are small, isolated changes.

---

### P0-1: Save Claim to MongoDB in ClaimOrchestrator

**Problem**  
`ClaimOrchestrator.process()` generates a `clm_xxx` ID and returns a `ClaimResult`, but never calls `ClaimEntityRepository.save()`. The `GET /api/claims/{claimId}` endpoint therefore always returns 404. Every in-flight claim is lost on server restart.

**Fix**  
In `ClaimOrchestrator`:
1. Create and save a `ClaimEntity` with status `RECEIVED` before processing begins.
2. Update the entity to `PROCESSING` when the pipeline starts.
3. Update to `COMPLETED` / `PARTIALLY_COMPLETED` / `FAILED` with the final `ClaimDecisionResult` at the end.
4. Wrap the save calls in try/catch — a MongoDB failure must not fail the claim decision.

**Files**
- `ClaimOrchestrator.java` — add save calls
- `ClaimEntityRepository.java` — already exists, no changes needed

**Effort:** ~2 hours  
**Risk:** Low — purely additive

---

### P0-2: Fix OcrService Returning null on Exception

**Problem**  
`PdfTextOcrService.extractText()` returns `null` on exception. Any caller that does not null-check before using the result will throw a `NullPointerException`.

**Fix**  
Change the catch block to return `""` instead of `null`. Update the `OcrService` interface contract Javadoc to state "never returns null; returns empty string on failure."

**Files**
- `PdfTextOcrService.java`
- `OcrService.java` (interface)

**Effort:** 30 minutes  
**Risk:** None

---

### P0-3: Enforce maxOcrCharsPerDoc

**Problem**  
`DocValidationProperties.Decision.maxOcrCharsPerDoc` is set to 4000 in config but is never read or applied in code. A large PDF sends unbounded text to the evidence extractor and downstream.

**Fix**  
In `PdfTextOcrService.extractText()`, after text extraction:
```java
String text = stripper.getText(doc);
if (text.length() > maxOcrCharsPerDoc) {
    text = text.substring(0, maxOcrCharsPerDoc);
}
return text;
```
Inject `DocValidationProperties` into `PdfTextOcrService`.

**Files**
- `PdfTextOcrService.java`
- `DocValidationProperties.java`

**Effort:** 1 hour  
**Risk:** Low

---

## Phase 1 — Performance (High Impact)

### P1-1: Async Pipeline — Return 202 Immediately

**Problem**  
`POST /api/claims/submit` blocks a Tomcat thread for the entire pipeline duration:
- OCR (PDFBox) for each file
- Embedding Ollama call (nomic-embed-text)
- Decision Ollama call (qwen3.5:9b)

On `qwen3.5:9b` locally, this is 15–60+ seconds per request. With Tomcat's default 200-thread pool and even modest concurrent usage, thread exhaustion will occur.

**Solution: Async + Poll pattern**

```
POST /api/claims/submit  →  202 Accepted  { claimId: "clm_xxx", status: "RECEIVED" }
                              ↓ (background thread pool)
                         pipeline runs asynchronously
                              ↓
GET  /api/claims/{claimId} →  { status: "PROCESSING" }   (during)
GET  /api/claims/{claimId} →  { status: "COMPLETED", decision: {...} }  (done)
```

**Implementation Steps**

1. **Configure async executor** in a `@Configuration` class:
   ```java
   @Bean("claimExecutor")
   public Executor claimExecutor() {
       ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
       exec.setCorePoolSize(4);
       exec.setMaxPoolSize(10);
       exec.setQueueCapacity(50);
       exec.setThreadNamePrefix("claim-");
       exec.initialize();
       return exec;
   }
   ```

2. **Modify `ClaimController.submit()`** to:
   - Generate `claimId` upfront
   - Save `ClaimEntity` with status `RECEIVED` synchronously
   - Submit pipeline to `@Async` method or `CompletableFuture.runAsync(..., claimExecutor)`
   - Return `ResponseEntity.accepted().body(new ClaimResult(claimId, RECEIVED, ...))`

3. **Modify `ClaimOrchestrator.process()`** — runs entirely on the async thread. Status updates go to MongoDB directly.

4. **`GET /api/claims/{claimId}`** already exists — it reads from `docvalidation_claims`. Once P0-1 is done, this works automatically.

**Files**
- `AsyncConfig.java` (new)
- `ClaimController.java`
- `ClaimOrchestrator.java`

**Effort:** 1 day  
**Risk:** Medium — changes the API contract. Frontend must be updated to poll.

---

### P1-2: Parallel Document Processing

**Problem**  
`ClaimOrchestrator` processes files sequentially in a `for` loop. Each file's OCR + relevance + extraction pipeline is independent. For a 3-file claim, total time = file1 + file2 + file3.

**Solution**  
Process all files concurrently using `CompletableFuture`:

```java
List<CompletableFuture<DocumentResult>> futures = files.stream()
    .map(file -> CompletableFuture.supplyAsync(
        () -> documentAgent.process(file, context), claimExecutor))
    .toList();

List<DocumentResult> results = futures.stream()
    .map(CompletableFuture::join)
    .toList();
```

For a 3-file claim, total time = time of the slowest single file (typically 3x speedup).

**Files**
- `ClaimOrchestrator.java`

**Effort:** 2 hours  
**Risk:** Low — `DocumentAgent` is stateless per file; `ClaimContext` must be made thread-safe for concurrent writes (use `ConcurrentLinkedQueue` for the document results list).

---

### P1-3: Configure Ollama Timeout

**Problem**  
No timeout is set on the Spring AI `ChatClient`. If Ollama hangs (model loading, OOM, swap), the calling thread blocks indefinitely.

**Fix**  
Add to `application.yml`:
```yaml
spring:
  ai:
    ollama:
      chat:
        options:
          num-predict: 512
      request-options:
        connect-timeout: 10s
        read-timeout: 60s
```

Also set `keepAlive` in `OllamaService.generateStructured()` to ensure the model stays loaded between calls:
```yaml
spring.ai.ollama.chat.options.keep-alive: 10m
```

**Files**
- `application.yml`

**Effort:** 30 minutes  
**Risk:** None

---

### P1-4: Embedding Cache

**Problem**  
`PolicyRagAgent` generates a fresh embedding for every claim via Ollama (`nomic-embed-text`). Claims with the same `claimType + claimReason + answers` pattern (very common in testing and repeat claims) pay the full Ollama round-trip every time.

**Solution**  
Add a Caffeine in-process cache keyed on the query string:

```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

```java
@Cacheable(value = "embeddings", key = "#query")
public float[] generateEmbedding(String query) { ... }
```

Configure in `application.yml`:
```yaml
spring:
  cache:
    caffeine:
      spec: maximumSize=500,expireAfterWrite=1h
```

For production with multiple instances, swap Caffeine for Redis without changing the `@Cacheable` annotation.

**Files**
- `OllamaEmbeddingService.java`
- `pom.xml`
- `application.yml`

**Effort:** 2 hours  
**Risk:** Low — cached embeddings are deterministic for the same model

---

## Phase 2 — Reliability & Observability

### P2-1: MDC Correlation Logging

**Problem**  
`claimId` is generated in `ClaimOrchestrator` but never added to the logging MDC. Log lines from different concurrent claims are interleaved with no way to filter by claim in production.

**Fix**  
In `ClaimOrchestrator.process()`, at the very start:
```java
MDC.put("claimId", context.getClaimId());
try {
    // ... pipeline ...
} finally {
    MDC.remove("claimId");
}
```

Update `logback` pattern to include `%X{claimId}`:
```xml
<pattern>%d{ISO8601} [%thread] [%X{claimId}] %-5level %logger - %msg%n</pattern>
```

**Files**
- `ClaimOrchestrator.java`
- `logback-spring.xml` (create if not present)

**Effort:** 1 hour  
**Risk:** None

---

### P2-2: Dead-Letter Queue for Ollama Failures

**Problem**  
When `ClaimDecisionAgent` catches an `OllamaServiceException`, it silently downgrades the decision to `MANUAL_REVIEW` with no retry and no operator alert. The claim appears "reviewed" but was never actually evaluated.

**Solution (short-term)**  
Persist the Ollama failure reason in `ClaimEntity` so operators can identify which MANUAL_REVIEW decisions were due to AI failure vs genuine ambiguity.

Add a field `aiFailureReason: String` to `ClaimEntity`. Set it when `OllamaServiceException` is caught.

**Solution (long-term)**  
Use a retry queue (Spring `@Scheduled` or a lightweight queue like MongoDB-backed job collection) to re-attempt the Ollama decision call after a cooldown period when Ollama recovers.

**Files**
- `ClaimEntity.java`
- `ClaimDecisionAgent.java`
- `ClaimOrchestrator.java`

**Effort:** 3 hours (short-term) / 1 day (retry queue)  
**Risk:** Low

---

### P2-3: Actuator Health + Metrics

Add Spring Boot Actuator with health checks for MongoDB and Ollama:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

Add a custom `HealthIndicator` that pings Ollama's `/api/tags` endpoint to confirm the model is loaded.

**Effort:** 2 hours  
**Risk:** None

---

## Phase 3 — Scalability

### P3-1: Replace In-Memory Cosine with MongoDB Vector Search

**Problem**  
`CosineMongoVectorService` loads all `policy_clauses` for a `claimType` into memory on every claim request, then computes cosine similarity in Java. At 22 medical clauses this is fine. When MOTOR, TRAVEL, and LIFE policies are ingested (expected: ~80–120 total clauses), this becomes a full collection scan per claim.

**Solution**  
The `MongoVectorService` interface is already designed for this swap. Implement `AtlasVectorSearchService` using MongoDB Atlas `$vectorSearch` aggregation pipeline, or for local MongoDB 7+ use the `$vectorSearch` operator with an Atlas-compatible index.

```java
@ConditionalOnProperty(name = "docvalidation.mongodb.use-atlas-vector-search", havingValue = "true")
public class AtlasVectorSearchService implements MongoVectorService { ... }
```

Toggle between implementations via config — no changes to `PolicyRagAgent`.

**Files**
- `AtlasVectorSearchService.java` (new)
- `application.yml`

**Effort:** 1 day  
**Risk:** Low — interface is already abstracted

---

### P3-2: Add MOTOR, TRAVEL, LIFE Policy Ingestion

The ingestion pipeline currently only handles the MEDICAL policy PDF. Extend `PolicyClauseSeeder` and add policy PDFs for the remaining three claim types. The `claimType`-scoped guard in the seeder already supports this pattern — one guard check per `claimType`.

**Effort:** 1 day per policy PDF (content-dependent)

---

### P3-3: Codebase Consolidation — Remove Dead Code

The following are legacy artefacts from the pre-docvalidation architecture that should be cleaned up:

- `claims.routing.auto-approve-max-amount` config — defined but unused (decision is now owned by `ClaimDecisionAgent`)
- `claims.upload.dir` config — defined but no file saving occurs
- Evaluate whether `RulesEngineService` GoRules path is still in use for the questionnaire flow, or whether it can be removed

Document the intent of the two-pipeline boundary (`claims.*` vs `docvalidation.*`) explicitly in a CLAUDE.md or architecture note so future developers understand the seam.

**Effort:** Half day

---

## Implementation Roadmap

```
Week 1:  Phase 0 — P0-1, P0-2, P0-3 (bug fixes)
Week 2:  Phase 1 — P1-3 (timeout), P1-2 (parallel docs), P1-4 (cache)
Week 3:  Phase 1 — P1-1 (async pipeline + frontend polling)
Week 4:  Phase 2 — P2-1 (MDC logging), P2-3 (actuator), P2-2 (dead-letter)
Week 5+: Phase 3 — P3-1 (Atlas vector search), P3-2 (more policies), P3-3 (cleanup)
```

> **Note:** P1-1 (async pipeline) is listed in Week 3 because it changes the API contract and requires coordinated frontend changes. All other Phase 1 items are backend-only and can be shipped independently.

---

## Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Frontend breaks on 202 response from async pipeline | High | High | Coordinate with frontend team; keep sync mode behind a feature flag during transition |
| Ollama model `qwen3.5:9b` not pulled locally | Medium | High | Add startup check / health indicator that logs a clear error if model is missing |
| MongoDB connection issues on restart (seen in logs) | High | High | P0-1 fix adds persistence; Phase 2 health checks add early detection |
| In-memory cache stale after model change | Low | Medium | Cache key includes model name; or flush on startup |
| Parallel document processing thread contention | Low | Medium | Use `ConcurrentLinkedQueue` in `ClaimContext`; test with 5+ files |

---

## Success Metrics

| Metric | Current | Target |
|--------|---------|--------|
| P99 latency for `/api/claims/submit` | 15–60s (blocking) | < 500ms (202 response) |
| Claim persistence on restart | 0% (lost) | 100% |
| Thread utilisation under 10 concurrent claims | ~100% (starved) | < 30% |
| Ollama calls per repeated claim pattern | 2 (always) | 1 (embedding cached) |
| Mean time to diagnose a claim in logs | Minutes (manual search) | Seconds (MDC filter) |

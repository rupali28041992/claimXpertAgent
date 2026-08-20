# Claim Assistant Backend — Architecture

One pipeline, one endpoint, one LLM call. `POST /api/claims/submit` runs file
validation, real OCR, top-K policy-clause retrieval (RAG), and exactly one
Ollama call that makes the final decision (APPROVED / REJECTED /
MANUAL_REVIEW) — no GoRules-driven approve/reject or readiness scoring, and
no separate LLM calls for document relevance or per-document validation
(both folded into that one call, for latency). GoRules is only used for one
static, non-decision lookup: the required-questions/required-documents list
per claim type. Nothing is persisted to Mongo in this flow.

## End-to-end flow

```
Angular Submit
  │  multipart: "claim" (JSON) + "files"
  ▼
ClaimController.submit()                     controller/ClaimController.java
  │  adapts ClaimSubmitRequest → ClaimRequest
  ▼
ClaimOrchestrator.process()                   docvalidation/agent/ClaimOrchestrator.java
  │
  ├─ 1. per file → DocumentAgent.process()
  │        ├─ FileValidationService.validate()        deterministic: mime type, size, readability
  │        └─ OcrService.extractText()                PDFBox real text extraction (PdfTextOcrService)
  │        (no LLM call here - relevance judgment moved into step 3)
  │
  ├─ 2. once per claim → PolicyRagAgent.findRelevantClauses()
  │        embeds "claimType claimReason" → CosineMongoVectorService ranks
  │        policy_clauses by cosine similarity → top-K clauses (no LLM call)
  │
  ├─ 3. once per claim → ClaimDecisionAgent.decide()
  │        THE ONE Ollama call in this flow. Given every valid document's OCR
  │        text + ALL top-K clauses, it judges relevance, clause satisfaction,
  │        AND the final outcome together → APPROVED / REJECTED / MANUAL_REVIEW
  │        + conditions + matchedClauses + reason. Short-circuits to
  │        MANUAL_REVIEW (no LLM call) if there's no evidence at all.
  │
  ▼
ClaimResult { claimId, status, documents[], decision }
```

`status` (pipeline outcome: did processing itself succeed) and `decision`
(business outcome: is the claim approved) are deliberately separate fields —
one tracks the plumbing, the other tracks Ollama's judgment. Nothing is
written to Mongo in this flow — `ClaimEntityRepository`/`ClaimEntity` still
exist and back `GET /api/claims/{claimId}`, but since nothing persists a
claim anymore, that lookup will return not-found for every new claim.

## One-time setup: policy clause ingestion

Before any claim can be decided, the policy PDF's clauses need embeddings in
Mongo. This is a manual, one-time action — not part of the request path:

```
POST /api/docvalidation/admin/policy-ingestion?claimType=MEDICAL
  (only registered if docvalidation.ingestion.enabled=true)
  ▼
PolicyIngestionController.ingest()            controller/PolicyIngestionController.java
  ▼
PolicyClauseIngestor.ingest()                 docvalidation/ingestion/PolicyClauseIngestor.java
  - PDFBox-extracts the policy PDF's text
  - splits on "SECTION X.Y" headers into clauses
  - embeds each clause, saves as PolicyClause docs
  - deletes-then-reinserts for that (claimType, sourceDocument) pair, so
    re-running during testing never duplicates
```

## Class reference

### Entry point & config

| Class | Purpose |
|---|---|
| `ClaimsAssistantApplication` | `@SpringBootApplication` main class. |
| `config/AiConfig` | Provides the Spring AI `ChatClient` bean every agent depends on. |
| `docvalidation/config/DocValidationProperties` | All `docvalidation.*` config: allowed file types/size, RAG `top-k`, decision's `max-ocr-chars-per-doc`, ingestion enable flag + PDF path. |

### Controllers

| Class | Endpoint(s) | Notes |
|---|---|---|
| `controller/ClaimController` | `POST /api/claims/submit`, `GET /api/claims/config/{claimType}`, `GET /api/claims/{claimId}` | The one entry point. `submit()` adapts the request and delegates to `ClaimOrchestrator`; `getConfig()` is the static GoRules lookup; `getClaim()` reads `docvalidation_claims` directly. |
| `controller/PolicyIngestionController` | `POST /api/docvalidation/admin/policy-ingestion` | Disabled by default (`@ConditionalOnProperty`) — the only access control, since this project has no Spring Security. |

### Orchestration & agents (`docvalidation/agent`)

| Class | One Ollama call? | Purpose |
|---|---|---|
| `ClaimOrchestrator` | — (coordinator) | Wires the whole sequence above; the only class that decides *what runs when*. |
| `DocumentAgent` | — (coordinator) | Per file: file validation → OCR. No LLM call — that used to be a per-document relevance check, now folded into `ClaimDecisionAgent`. |
| `PolicyRagAgent` | No (pure retrieval) | Embeds the claim query, asks `MongoVectorService` for the top-K nearest clauses. |
| `ClaimDecisionAgent` | **Yes — the only LLM call in the flow, 1 per claim** | Judges document relevance, clause satisfaction, AND the final outcome in one prompt. Degrades to `MANUAL_REVIEW` on Ollama failure or missing evidence — never a forced guess. |

### Services (`docvalidation/service`)

| Class | Purpose |
|---|---|
| `FileValidationService` | Deterministic checks only: null/empty, mime type allow-list, size limit, stream readability. |
| `OcrService` / `PdfTextOcrService` | Real PDFBox text extraction for `application/pdf`; images return `""` (no OCR engine wired in — known limitation). |
| `EmbeddingService` / `OllamaEmbeddingService` | Wraps Spring AI's `EmbeddingModel` (`nomic-embed-text`). |
| `MongoVectorService` / `CosineMongoVectorService` | Filters `policy_clauses` by claim type (+ reason if it matches), ranks the small candidate set by in-Java cosine similarity, returns top-K. No Atlas `$vectorSearch` — MongoDB here is Community edition. |
| `OllamaService` | Thin wrapper around `ChatClient` — `generate()`/`generateStructured()`. No retries; translates any failure into `OllamaServiceException` so nothing raw ever reaches a caller. |
| `OllamaServiceException` | Carries a `Code` (`OLLAMA_UNAVAILABLE` / `OLLAMA_TIMEOUT` / `OLLAMA_INVALID_RESPONSE`) every agent catches and degrades on. |

### Ingestion (`docvalidation/ingestion`)

| Class | Purpose |
|---|---|
| `PolicyClauseIngestor` | One-time PDF → embedded `PolicyClause` documents, described above. |

### Models (`docvalidation/model`)

| Class | Purpose |
|---|---|
| `ClaimContext` | Mutable state threaded through one claim's processing: type/reason/answers, documents, retrieved clauses, decision, status. |
| `ClaimRequest` | Input to `ClaimOrchestrator.process()` — `claimType`, `claimReason`, `answers` (map). |
| `ClaimResult` | The API response — `claimId`, `status`, `documents[]`, `decision`. |
| `ClaimEntity` | Shape for the `docvalidation_claims` collection — no longer written to by `/submit` (persistence was removed for latency), but `ClaimEntityRepository`/`GET /api/claims/{claimId}` still read it. |
| `DocumentResult` | Per-file outcome: validity, errors, OCR text, status. Nothing about relevance or clause satisfaction lives here anymore — that's all inside `ClaimDecisionResult` now. |
| `PolicyClause` | One ingested policy section: `claimType`, `claimReason` (the section title), `clauseText`, `embedding`, `sourceDocument`. |
| `ClaimDecisionResult` | The final verdict: `decision`, `conditions[]`, `matchedClauses[]`, `confidence`, `reason`. |
| `ClaimProcessingStatus` (enum) | `RECEIVED / COMPLETED / PARTIALLY_COMPLETED / FAILED` — pipeline outcome only. |
| `ClaimDecisionStatus` (enum) | `APPROVED / REJECTED / MANUAL_REVIEW` — business outcome only. |
| `DocumentStatus` (enum) | Per-document lifecycle state. |

### Repositories (`docvalidation/repository`)

| Class | Collection |
|---|---|
| `ClaimEntityRepository` | `docvalidation_claims` |
| `PolicyClauseRepository` | `policy_clauses` — includes `deleteByClaimTypeAndSourceDocument` for safe re-ingestion. |

### Exception handling

| Class | Purpose |
|---|---|
| `ClaimException` | Thrown for request-level failures (e.g. malformed multipart JSON). |
| `GlobalExceptionHandler` | `@RestControllerAdvice`, maps `ClaimException` → `400`. Scoped to this module only. |

### What's left of the legacy package, and why

| Class | Why it still exists |
|---|---|
| `rules/RulesEngineService` | Now only `getClaimTypeConfig()` — a static lookup (which fields/documents a claim type needs), not a decision. Backs `GET /api/claims/config/{claimType}` for Screens 3 & 4. |
| `rules/ZenDecisionRunner` | Thin GoRules JDM engine wrapper `RulesEngineService` depends on. |
| `rules/ClaimTypeConfig` | Record shape for that lookup's result. |
| `dto/ClaimSubmitRequest` | The multipart request DTO the frontend already sends — kept as-is so no frontend request-building code had to change; adapted internally to `ClaimRequest`. |
| `model/ClaimAnswer` | One answer entry inside `ClaimSubmitRequest`. |

Everything else that used to live in `com.nextgen.claims.agent/service/rag/repository/model` for the old GoRules-routed flow (`ValidationAgent`, `OcrExtractionService`, `FileStorageService`, `ReadinessScoreCalculator`, `PolicyClauseRetriever`, `ClaimService`, `ReviewController`/`ReviewService`, the legacy `Claim`/`ClaimDocument`/`ClaimStatus`/`StatusChange` models, `claim-routing.json`) has been removed — none of it had any caller left once `/submit` moved to the `docvalidation` pipeline.

## Config reference (`application.yml`, `docvalidation.*`)

| Key | Default | Used by |
|---|---|---|
| `document.max-file-size` | 10485760 | `FileValidationService` |
| `document.allowed-mime-types` | pdf, jpeg, png | `FileValidationService` |
| `rag.top-k` | 4 | `ClaimOrchestrator` → `PolicyRagAgent` |
| `decision.max-ocr-chars-per-doc` | 4000 | `ClaimDecisionAgent` (prompt-size cap) |
| `ingestion.enabled` | false | Gates `PolicyIngestionController`'s existence |
| `ingestion.medical-policy-path` | — | `PolicyIngestionController` |
| `mongodb.policy-vector-index` | — | Documented placeholder for a future Atlas `$vectorSearch` migration; unused today (Community edition, in-Java cosine ranking). |

## Frontend integration

`claim-api.service.ts` still POSTs to `/api/claims/submit` with the same multipart
shape (`claim` JSON + `files`); only the response type changed, from the old
`ClaimSubmitResponse` to `ClaimResult` (`claim-api.model.ts`). `chat-portal.component.ts`
now reads `response.decision` and `response.status` instead of `readinessScore`/
`flags`/`fileErrors`, and the confirmation screen (`chat-portal.component.html`)
shows the actual decision instead of a generic "we'll review in 2–3 days" message.

# NextGen Claims Assistant — Backend

Spring Boot backend for an agentic insurance claims intake prototype. Covers
Life, Medical, Motor, and Travel claims: collects claim details and
documents in one submit call, validates uploaded documents against policy
wording using an LLM + RAG, and routes the claim to auto-approve,
auto-reject, or human review using GoRules decision graphs.

**Design principle:** AI only where judgment over unstructured text is
required (classifying free text, validating a document against policy
wording). Money, dates, and the approve/reject decision are always plain
Java or GoRules decision graphs — never the model.

## Stack

- Java 21, Spring Boot 3.3.2
- MongoDB (single `claims` collection with nested answers/documents/status
  history, plus one `policy_clause_vectors` collection for RAG)
- Spring AI + [Ollama](https://ollama.com) (local LLM — chat model for
  agents, embedding model for RAG)
- [GoRules ZEN Engine](https://gorules.io) (`io.gorules:zen-engine`) —
  business rules as editable JSON decision graphs, not hardcoded Java

## Prerequisites

| Tool | Version | Check |
|---|---|---|
| JDK | 21 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| MongoDB | running on `localhost:27017` | see below |
| Ollama | running on `localhost:11434`, with `llama3` and `nomic-embed-text` pulled | see below |

### MongoDB

```powershell
docker run -d --name claims-mongo -p 27017:27017 mongo:7
```
Or install MongoDB Community Server directly and let it run as a Windows
service (default port `27017`). No manual schema setup needed — Spring Data
creates the `claims` collection automatically on first write. You do need
to seed `policy_clause_vectors` manually (see [RAG](#rag--policy_clause_vectors)
below) before `ValidationAgent` has anything to retrieve.

### Ollama

```powershell
ollama pull llama3
ollama pull nomic-embed-text
ollama serve
```

### JAVA_HOME (Windows)

Maven needs `JAVA_HOME` pointed at a JDK 21 install:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.12.8"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```
Set permanently via System Environment Variables to avoid repeating this
per terminal session.

## Running

```powershell
cd claim-assistant-backend
mvn clean install
mvn spring-boot:run
```

App starts on `http://localhost:8080`. Confirm MongoDB connected — look for
a `Monitor thread successfully connected to server` line in the startup
log. Confirm GoRules decision graphs loaded — no error from
`RulesEngineService`'s `@PostConstruct` step.

Smoke test (no MongoDB write, just proves the app + GoRules are up):
```powershell
curl http://localhost:8080/api/claims/config/LIFE
```

## Configuration

All in `src/main/resources/application.yml`:

| Key | Purpose |
|---|---|
| `spring.data.mongodb.uri` | MongoDB connection string |
| `spring.ai.ollama.base-url` | Ollama server address |
| `spring.ai.ollama.chat.model` | Chat model used by all agents |
| `spring.ai.ollama.embedding.model` | Embedding model used by RAG |
| `claims.routing.auto-approve-max-amount` | Threshold fed into `claim-routing.json`'s GoRules graph |
| `claims.upload.dir` | Local disk folder for uploaded files |
| `claims.cors.allowed-origin` | Allowed frontend origin (Angular dev server) |

## API

| Method | Path | Purpose | AI / RAG? |
|---|---|---|---|
| `GET` | `/api/claims/policy/{policyNumber}` | Resolve `customerId`/`claimType` from a customer-typed policy number | No — Mongo lookup |
| `POST` | `/api/claims/suggest-type` | Free text → suggested `claimType`/`claimReason` | AI, no RAG |
| `GET` | `/api/claims/config/{claimType}` | Questions + required documents for a claim type | No — GoRules lookup |
| `POST` | `/api/claims/submit` | Submit a full claim (multipart: `claim` JSON part + `files` parts) | AI + RAG, once per uploaded file |
| `GET` | `/api/claims/{claimId}` | Fetch a claim (for status tracking) | No |
| `POST` | `/api/review/{claimId}/approve` | Adjuster approves an `UNDER_REVIEW` claim | No |
| `POST` | `/api/review/{claimId}/reject` | Adjuster rejects, with a reason | No |

### `POST /api/claims/submit`

Multipart request:
- part `claim` — JSON string matching `ClaimSubmitRequest`:
  ```json
  {
    "customerId": "cust_1",
    "policyId": "pol_1",
    "claimType": "LIFE",
    "claimReason": "Natural Death",
    "freeText": "my father passed away...",
    "answers": [
      { "questionId": "dateOfDeath", "questionText": "Date of death", "answerText": "2026-07-02" }
    ]
  }
  ```
- part `files` — one or more uploaded documents

Response (`ClaimSubmitResponse`):
```json
{
  "claimId": "clm_9f3a2b1c",
  "readinessScore": 82,
  "flags": ["mismatch:hospitalName"],
  "summary": "Your claim looks mostly complete. 1 item(s) flagged...",
  "status": "UNDER_REVIEW",
  "fileErrors": null
}
```
If any uploaded file fails the plain (non-AI) validity check — empty,
unreadable, wrong type — the whole submission is rejected with
`fileErrors` populated and **nothing is written to MongoDB**.

## Request flow (what happens inside `/submit`)

```
ClaimController.submit()
   └─ ClaimService.submit()
        1. generate claimId
        2. for each uploaded file:
             OcrExtractionService.cheapValidate()   — reject bad files, no AI spent
             OcrExtractionService.extract()          — pull text out of the file
             FileStorageService.store()                — save to disk, get back a path
             ValidationAgent.validate()                  — the only mandatory AI + RAG step
        3. if any file failed step 2 → bounce back now, no MongoDB write
        4. RulesEngineService.getClaimTypeConfig()      — GoRules: required document count
           ReadinessScoreCalculator.calculate()           — plain weighted formula, no AI
        5. RulesEngineService.evaluateHardRules()        — plain Java (documents present?)
           RulesEngineService.route()                      — GoRules: AUTO_APPROVED / AUTO_REJECTED / UNDER_REVIEW
        6. claimRepository.save(claim)                      — ONE MongoDB write, whole claim nested
```

`ClaimService` never does OCR, file I/O, or rules evaluation itself — it
only calls the specialist for each step and assembles the result.

## Package structure

```
com.nextgen.claims
├── model/        MongoDB document shapes (Claim, ClaimAnswer, ClaimDocument,
│                 ClaimStatus, StatusChange, PolicyClauseVector)
├── repository/   Spring Data MongoDB repositories
├── dto/          Request/response shapes for the HTTP layer only
├── agent/        Spring AI + Ollama calls (ValidationAgent, IntentClassificationAgent)
├── rag/          PolicyClauseRetriever — the one retrieval step, used only by ValidationAgent
├── rules/        GoRules wiring (ZenDecisionRunner, RulesEngineService) + ReadinessScoreCalculator
├── service/      Orchestration (ClaimService, OcrExtractionService, FileStorageService, ReviewService)
├── controller/   HTTP endpoints (ClaimController, ReviewController)
└── config/       Spring bean wiring (AiConfig — the shared ChatClient)
```

## MongoDB collections

| Collection | Written | Contents |
|---|---|---|
| `claims` | Once per submit (insert), then updated in place for adjuster review | The whole claim — answers, documents, flags, status history, all nested in one document |
| `policy_clause_vectors` | Seeded manually once per product/rider, never during a claim | Policy clause text + its embedding — the source `PolicyClauseRetriever` searches |

MongoDB Community has no native `$vectorSearch` (Atlas-only). This
prototype pre-filters `policy_clause_vectors` by `productType` (plain
query) then ranks the small remaining set by cosine similarity in Java —
see `PolicyClauseRetriever`. Swap for a real Atlas Vector Search index
later without changing `ValidationAgent`.

## Claim types & questions

There are **4 claim types** configured, all inside
`src/main/resources/rules/claim-type-config.json`. This is the single
source of truth — `GET /api/claims/config/{claimType}` returns exactly
what's below, live from GoRules, not hardcoded anywhere else.

### LIFE

| questionId | questionText | fieldType |
|---|---|---|
| `dateOfDeath` | Date of death | date |
| `hospital` | Hospital/place | text |
| `causeOfDeath` | Cause of death | dropdown |
| `nomineeRelation` | Nominee relationship | dropdown |

Required documents: Death Certificate, Policy Bond, ID Proof (Nominee),
Hospital / Medical Record

### MEDICAL

| questionId | questionText | fieldType |
|---|---|---|
| `admissionDate` | Date of admission | date |
| `hospital` | Hospital name | text |
| `diagnosis` | Diagnosis | text |

Required documents: Discharge Summary, Hospital Bill, Policy Bond, ID Proof

### MOTOR

| questionId | questionText | fieldType |
|---|---|---|
| `incidentDate` | Date of incident | date |
| `incidentType` | Type of incident | dropdown |

Required documents: RC Copy, Driving License, Repair Estimate

### TRAVEL

| questionId | questionText | fieldType |
|---|---|---|
| `tripDate` | Date of trip | date |
| `issueType` | What happened | dropdown |

Required documents: Boarding Pass, Airline Delay Certificate, Policy Bond

> Note: `dropdown` fields (`causeOfDeath`, `nomineeRelation`,
> `incidentType`, `issueType`) don't carry an `options` list in the
> decision graph yet — the actual option values are not yet defined here
> and would need to be added to `CONFIG` (or hardcoded in the frontend)
> before the UI can render real dropdown choices.

Verify live at any time:
```powershell
curl http://localhost:8080/api/claims/config/LIFE
curl http://localhost:8080/api/claims/config/MEDICAL
curl http://localhost:8080/api/claims/config/MOTOR
curl http://localhost:8080/api/claims/config/TRAVEL
```

## RAG — where it is, and where it deliberately isn't

RAG exists in exactly one place: `ValidationAgent`, via
`PolicyClauseRetriever`. Nothing else in the system uses it.

```
ValidationAgent.validate(claimType, claimReason, ocrText, extractedFields, answers)
  └─ PolicyClauseRetriever.retrieveRelevantClauses(claimType, claimReason)
       1. repository.findByProductType(claimType)             — plain Mongo query
       2. embeddingModel.embed("Claim type: X. Reason: Y")     — query → vector
       3. cosine similarity over candidates, top 3 returned
  └─ prompt = [retrieved clauses] + [OCR text] + [typed answers]
       → Ollama → { flags: [...], clauseSatisfied: true/false }
```

Intent/Classification skip RAG — only 4 fixed claim types, a zero-shot
prompt is enough. Questions and required documents skip RAG too — they're
deterministic per claim type, handled by GoRules, not retrieval.

### Seeding `policy_clause_vectors`

No automated seeding is wired up yet. Insert manually via MongoDB Compass
or `mongosh`, one document per policy clause:
```json
{
  "productType": "LIFE",
  "riderCode": null,
  "section": "Waiting Period",
  "clauseText": "There is a waiting period of 90 days from the policy start date for claims arising from natural causes.",
  "embedding": [0.01, 0.02, 0.03, "... same dimension as nomic-embed-text output"]
}
```

### Seeding `policies`

`GET /api/claims/policy/{policyNumber}` (Screen 1's policy-number lookup)
reads from this collection. Like `policy_clause_vectors`, nothing seeds it
automatically - insert test policies via `mongosh`:
```javascript
db.policies.insertMany([
  { _id: "POL-2024-00123", customerId: "cust_1", claimType: "LIFE",
    policyholderName: "Rajesh Kumar", active: true,
    startDate: ISODate("2022-01-15T00:00:00Z"), endDate: ISODate("2032-01-14T00:00:00Z"),
    sumInsured: 2500000 },
  { _id: "POL-2024-00456", customerId: "cust_2", claimType: "MEDICAL",
    policyholderName: "Anita Sharma", active: true,
    startDate: ISODate("2025-04-01T00:00:00Z"), endDate: ISODate("2026-03-31T00:00:00Z"),
    sumInsured: 500000 },
  { _id: "POL-2024-00789", customerId: "cust_3", claimType: "MOTOR",
    policyholderName: "Vikram Singh", active: true,
    startDate: ISODate("2025-11-10T00:00:00Z"), endDate: ISODate("2026-11-09T00:00:00Z"),
    sumInsured: 800000 },
  { _id: "POL-2024-01011", customerId: "cust_4", claimType: "TRAVEL",
    policyholderName: "Priya Menon", active: true,
    startDate: ISODate("2026-07-01T00:00:00Z"), endDate: ISODate("2026-07-20T00:00:00Z"),
    sumInsured: 100000 }
]);
```
`_id` is used directly since `Policy.policyNumber` is mapped with `@Id`,
same as `Claim.claimId`.

## GoRules — business rules as editable JSON, not Java

Two decision graphs, loaded once at startup by `RulesEngineService`
(`@PostConstruct`) via `ZenDecisionRunner`, which wraps the real
`io.gorules:zen-engine` engine:

| File | Purpose |
|---|---|
| `src/main/resources/rules/claim-type-config.json` | Questions + required documents, keyed by claim type |
| `src/main/resources/rules/claim-routing.json` | Auto-approve / auto-reject / under-review logic |

Each file is a GoRules JDM graph (`Request → functionNode → Response`);
the actual logic lives as JavaScript inside the middle node's `content`
field. `RulesEngineService` sends in a plain Java `Map`, GoRules runs the
JS, and returns a plain `Map` back — no AI involved anywhere in this path.

### Editing the rules

**Directly (fastest, no tooling):** open the `.json` file in any text
editor, edit the JS object inside the `content` field of the middle
(`functionNode`) node, save, restart the app. No other file needs to
change — `RulesEngineService`/`ZenDecisionRunner` only read the file
contents at startup.

**Visually, using the GoRules editor (for non-developers):**

1. Go to [gorules.github.io/jdm-editor](https://gorules.github.io/jdm-editor/)
   in a browser. It's free, standalone, no login and no server connection —
   it only edits a JSON blob in your browser.
2. Open `src/main/resources/rules/claim-type-config.json` (or
   `claim-routing.json`) in a text editor on the side, copy its full
   contents.
3. In the GoRules editor, use **Import** (or paste over the default
   starter graph) with that JSON. You'll see it render as 3 connected
   boxes: `Request → Claim Type Config → Response`.
4. Click the middle box (`Claim Type Config`). This opens a code panel
   showing the JavaScript `CONFIG` object and the `handler` function —
   this is the actual questions/documents data from the
   [Claim types & questions](#claim-types--questions) section above.
5. Edit directly in that code panel. Common edits:
   - **Add a question to an existing claim type** — add a new object to
     that type's `questions` array:
     ```js
     { questionId: 'policeReportNumber', questionText: 'Police report number', fieldType: 'text' }
     ```
   - **Add a required document** — add a string to that type's
     `requiredDocuments` array.
   - **Add a whole new claim type** (e.g. `PROPERTY`) — add a new top-level
     key to `CONFIG` with its own `questions`/`requiredDocuments`, following
     the same shape as `LIFE`/`MEDICAL`/etc. The `handler` function needs no
     changes — it already looks up `CONFIG[input.claimType]` generically.
   - **Add dropdown options** — since `fieldType: 'dropdown'` questions
     don't carry an options list yet, add one, e.g.
     `{ questionId: 'incidentType', questionText: 'Type of incident', fieldType: 'dropdown', options: ['Collision', 'Theft', 'Fire'] }`.
6. Use the editor's built-in **simulator/test panel** to send a sample
   input like `{"claimType": "LIFE"}` and confirm the output looks right
   before exporting — this catches typos/broken JS early, without needing
   to restart Spring Boot to find out.
7. Click **Export** (downloads the updated JSON file to your computer).
8. Replace the existing file at `src/main/resources/rules/claim-type-config.json`
   with the exported one (same filename, same folder).
9. Restart the Spring Boot app (`mvn spring-boot:run`) — `RulesEngineService`
   reloads the file at startup via `@PostConstruct`, so the new questions/
   documents take effect immediately without touching any Java code.

This is a manual file handoff, not a live connection — the free editor
can't save directly into the running app; someone still has to move the
exported file into the project and restart. Zero-touch editing for
business (no developer in the loop, changes live immediately, no restart)
requires GoRules' paid, self-hosted BRMS server (Docker + Postgres +
object storage, and it needs outbound internet for license checks) — out
of scope for this prototype.

## Known prototype limitations

- `OcrExtractionService.extract()` is a stub — wire in a real OCR library
  (Tesseract, cloud Vision API, etc.) before this does anything useful.
- `RulesEngineService.evaluateHardRules()` only checks "are there any
  documents at all" — policy validity, sum insured limits, waiting-period
  checks, and duplicate-claim detection are not yet implemented.
- No payment/payout calculation — deliberately out of scope for this
  prototype (see `ReviewService`).
- `policy_clause_vectors` embeddings must be seeded manually; there's no
  admin endpoint or batch job for it yet.
- File uploads are stored on local disk (`claims.upload.dir`), not
  object storage.

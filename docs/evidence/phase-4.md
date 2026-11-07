# Phase 4 verification — 2026-11-07

Executed in the existing Ubuntu workspace with Docker Engine 29.9.0 / Compose v5.6.0. Live Gemini requests, browser rendering/clicks, and a clean-clone run are not claimed.

| Command / check | Observed result |
| --- | --- |
| Backend test image / `make test` | Java 21/Maven verify, 35 tests, no failures/errors/skips |
| Frontend test stage and `docker run --rm structiq-frontend-test npm test` | TypeScript lint/build passed; 21 tests across 4 files passed |
| AI test image / `make test` | 28 pytest cases passed; 1 existing Starlette HTTPX deprecation warning |
| `make test` | Exit 0; all three image test targets and Compose boundary assertions passed |
| `make up` | Exit 0; all four normal services healthy; PostgreSQL V4/V5 migrations applied |
| `make smoke`, `make smoke-phase1`, `make smoke-phase2`, `make smoke-phase3` | Passed on the updated normal stack |
| `make smoke-phase4` | Passed: AI_NOT_CONFIGURED 503, project/dashboard remain usable; no provider request |
| `make proof-rag` | Passed in isolated test stack; final replay after citation changes also passed; test containers/networks/volumes removed |
| Real PostgreSQL vector proof inside test stack | WHERE-before-top-1 despite closer foreign chunks; READY/model exclusion; scoped composite FK and vector(768) dimension rejection passed |
| OpenAPI inspection | Version 0.5.0; status/ask/index paths, Question `{question}`, Answer `{answer,citations,retrievalCount}` and scoped Citation schema verified |
| Compose network checks | PostgreSQL isolated; AI/DB host ports absent; AI provider-egress bridge explicit; loopback web/API; private parser mount read-only; nginx has no private mount/key |
| Python compile, ignored files and diff check | App/scripts compile; `.env` and demo credentials ignored; `git diff --check` passes |
| Browser inventory | No browsers/apps; host unavailable. UI visual/click E2E blocked |

Backend additions prove authorization before AI calls, guessed project denial, viewer indexing denial and document/project mismatch; explicit no-key 503 without breaking core flows; valid allowed READY citations accepted while cross-project, fabricated filename/excerpt, stale/failed/deleted chunks and unsupported uncited answers are rejected. Existing authentication, membership, task, dashboard and document tests remain passing. H2 runs common migrations through V4 for API/authorization proof; PostgreSQL-only V5 is tested separately in the real vector stack.

AI tests cover chunk/page/overlap limits, SQL ordering and project/READY/model/dimension predicates, citation derivation from retrieved rows, relevance/empty-context refusal without generation, malformed/fabricated/cross-project source denial, normalized 768-dimension REST embeddings with the correct retrieval task, no tools and explicit untrusted JSON context in generation, provider error sanitization, no-key/mismatched-dimension handling, operation-bound HMAC/replay protection, and Unicode excerpts within the Java API's UTF-16 budget. Gemini HTTP responses are mocked. Existing parser/resource/auth tests remain passing.

Frontend additions verify no-key help, configured empty-question guidance, escaped answer/citation rendering with human-verification text, and sending only a question through the authorized project path. These are client and React server-rendering tests using test-only fixtures, not browser interactions.

The isolated full-stack proof uses a dedicated `structiq-rag-proof` Compose project and a provider fixture present only in the test image. It runs actual nginx → Spring → HMAC FastAPI → PostgreSQL pgvector calls. PDF upload automatically indexes to READY; another project receives closer vectors; a real top-1 query still returns the caller's project. READY/model exclusion, FK and vector-dimension checks run directly against PostgreSQL. API questions return actual retrieved citation metadata and byte-identical authorized source downloads. Other-project URLs, prompt-supplied project scope, embedded hostile instructions, viewer indexing and revoked membership cannot expose the second project's secret fixture. Empty/irrelevant context returns the fixed refusal. Document deletion removes vector chunks and prevents later citations. These tests prove the permission barrier independently of a model; they do not demonstrate live-model factual accuracy or universal prompt-injection resistance.

No production mock mode was introduced. The normal runtime image excludes the `tests/` provider fixture, and default Compose uses the real Gemini adapter. The proof explicitly clears provider-key/model environment and makes no Gemini request. Its `finally` cleanup destroys only its named disposable test volumes; a final proof `compose ps` returned no containers. The normal localhost:5173 stack remains healthy and running.

Issues resolved/observed: the proof's initial backend host port 8081 was occupied. That unnecessary publication was removed; only test frontend 5174 is published. The test image now provisions shared-volume ownership consistently with runtime. Running all phase smoke logins in one minute reached the existing 10-attempt limit; no-key smoke now retries once after the next window rather than weakening rate limits. Citation excerpts were bounded for cross-language Unicode length. An extra Spring guard rejects unsupported uncited freeform answers, and its regression passed.

Remaining limitations: no real Gemini key/account/model response or quality calibration was tested; relevance cutoff 0.35 is conservative and unbenchmarked. Browser host is unavailable. Indexing/questions hold a project lock and can delay other writes; provider calls have bounded requests/batches/deadlines but no per-user AI quota or durable work queue. Local AI DB credentials are broad demo credentials; production needs restricted grants. Replay state is single-instance. Existing upload/orphan/OCR/antivirus and login/TLS/proxy limitations persist. Scans, Jenkins, SonarQube and browser E2E remain Phase 5 work, not passed checks.

Modified groups: backend AI controller/service/HMAC client, document auto-index integration, common/vector migrations and API tests; AI Gemini adapter, chunk/retrieval/index service, signed internal operation routing and tests; project AI/index UI and tests; provider egress/configuration, test-only Compose override and proof scripts; README/API/threat/progress/evidence and Make targets. Prior Phase 3 working-tree changes were preserved. No commit or live Gemini success is claimed for this continuation.

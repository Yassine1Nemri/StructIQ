# Implementation progress

## Phase 0 — Foundation (completed 2026-11-07)

- [x] Java 21 / Spring Boot API skeleton with public sanitized health and system status.
- [x] React / TypeScript shell with a real status request, loading, error, and retry states.
- [x] Internal FastAPI health service; no provider key required.
- [x] PostgreSQL / pgvector bootstrap, private network, healthchecks, localhost web/API ports.
- [x] Dockerfiles, build-context exclusions, Make targets, foundation Jenkins pipeline.
- [x] Secret-generation script, ignored `.env`, README, frontend lockfile and Vite declarations.
- [x] Backend container build: five tests passed.
- [x] AI container build: three tests passed (one dependency deprecation warning).
- [x] Resolved Compose configuration and network-boundary assertions passed.
- [x] Frontend container lint, three tests, production build passed.
- [x] `make test` passed across all three container test stages and Compose assertions.
- [x] `make up` exited successfully; all four services healthy.
- [x] `make smoke` passed: web, proxy, database readiness, internal AI health, pgvector 0.8.2, protected API 401.
- [ ] Visual browser QA / screenshots blocked: browser access host unavailable.

The existing repository had no commits, README, progress notes, or frontend lockfile. This continuation finishes the foundation before Phase 1. See `docs/evidence/phase-0.md` for final observed checks.

Phase 0 introduced no authentication or business workflows. Phase 1 follows below. Browser screenshots remain an outstanding check; foundation HTTP smoke verification is complete.


## Phase 1 — Authentication and project boundaries (completed 2026-11-07)

- [x] Flyway V1 schema for users, projects, memberships, constraints, indexes, and cascade cleanup.
- [x] BCrypt cost 12, 15-minute HS256 JWTs, issuer/audience/expiry/subject validation, current database roles, `/auth/me`.
- [x] Stateless bearer security, expanded CORS allowlist methods, sanitized errors and correlation IDs, bounded login rate limiting.
- [x] Scoped project list/search/status/pagination, creation, detail, edit/archive/delete, automatic creator manager membership.
- [x] Exact-email membership addition, role changes, removal, scoped membership IDs, serialized last-manager protection.
- [x] React login/projects/detail/member management/profile, memory-only token, expiry/sign-out cache cleanup, responsive styles and error states.
- [x] Opt-in deterministic synthetic seed; generated password in ignored mode-0600 credentials file.
- [x] Local-only generated OpenAPI/Swagger, API examples, updated README and initial threat model.
- [x] Backend HTTP/H2 integration tests and rate-limiter unit tests passed; PostgreSQL live smoke passed through nginx.
- [x] `make up`, `make seed`, `make smoke`, `make smoke-phase1`, and `make test` passed.
- [x] Final backend run passed 17 tests, including documentation-default and missing-expiry regression checks; frontend passed 6 tests, AI retains 3 passing tests.
- [ ] Browser UI QA blocked: no browser surfaces; browser host unavailable. No screenshots or E2E result claimed.

See `docs/evidence/phase-1.md` for final observed counts, commands, and limits. JDBC repositories intentionally keep authorization filters explicit; the same Flyway schema is exercised on H2 and on the live PostgreSQL stack. No Testcontainers result is claimed.

At the Phase 1 stop point, tasks/dashboard were next. Remaining after Phase 2: Phase 3 private documents; Phase 4 secure RAG; Phase 5 DevSecOps and end-to-end proof. Date PATCH values are retained on null/omission; clearing dates is not implemented. Login limiting is single-instance and shared by local nginx-proxied users. AI remains unimplemented and requires no key for the current demo.

Phase 2 tasks and dashboard are implemented below.


## Phase 2 — Tasks and dashboard (completed 2026-11-07)

- [x] Flyway V2 tasks with status/priority constraints, timestamps, scoped indexes and assignee membership foreign key.
- [x] Manager/admin task CRUD; member-only own-status updates; viewer read access; project/task ID mismatch denial.
- [x] Presence-aware task PATCH, unassignment and deadline clearing, input validation, filtered/sorted/paginated task lists.
- [x] My Tasks scoped to authenticated assignee and current memberships in SQL.
- [x] Member removal atomically unassigns project tasks; project deletion clears tasks and memberships under the project lock.
- [x] Consistent snapshot dashboard counts, real grouped status charts, overdue/upcoming work, and task-derived project progress.
- [x] React Overview, My Tasks, project task list/editor, member assignment, status controls, loading/error/empty states, and cache invalidation.
- [x] Five deterministic synthetic task IDs; due dates relative to first creation; existing task edits preserved on seed.
- [x] Backend 25 tests passed (8 new task/dashboard tests); frontend 12 tests passed (client plus component rendering); unchanged AI 3 tests passed.
- [x] `make test`, `make up`, `make seed`, `make smoke`, `make smoke-phase1`, and `make smoke-phase2` passed.
- [x] Generated OpenAPI 0.3.0 documents task/dashboard paths and distinct TaskCreate/TaskPage schemas.
- [ ] Browser visual QA and click-based E2E blocked/unrun: browser access host unavailable. Component server rendering and live HTTP tests passed; no browser result claimed.

Evidence and changed-file groups: `docs/evidence/phase-2.md`. Testcontainers, provider calls, Jenkins, and vulnerability scans remain unclaimed. Existing project dates still cannot be cleared via PATCH; task deadlines can. Status transitions allow any known state, and edits use last-writer-wins under project-level locking. Production login/proxy limitations from Phase 1 remain.

Phase 3 private documents are implemented below; secure RAG remains Phase 4.


## Phase 3 — Private documents (completed 2026-11-07)

- [x] Flyway V3 document metadata, private extracted pages, scoped constraints and indexes, durable file-deletion outbox.
- [x] PDF/TXT multipart upload, default 10 MiB cap (configurable downward), strict content/type/extension checks, safe display names, generated UUID storage and private checksums.
- [x] Project-scoped listing, attachment downloads, role/uploader deletion and extraction retry; membership and both resource IDs checked on Spring.
- [x] Private volume: backend UID/GID 10001 read/write, parser supplementary group 10001 read-only, no nginx mount or public AI/DB ports.
- [x] HMAC-authenticated internal parser requests with timestamp/nonce/body binding and replay rejection.
- [x] pypdf 7.0.0 PDF page extraction and UTF-8 TXT extraction in resource-limited subprocess; explicit encrypted/scanned/invalid/timeout failures; no Gemini key needed.
- [x] Honest synchronous PROCESSING → UPLOADED/text-extracted-AI-pending or FAILED; READY reserved for Phase 4. Interrupted processing supports retry after failure recovery.
- [x] Document/project deletion removes private extracted pages and queues file cleanup atomically; immediate after-commit attempt and scheduled retries; outage retry test passed.
- [x] React Documents panel with selection/drop, size instructions, real upload progress, metadata/status/failure states, bearer download, deletion and retry permissions.
- [x] Backend 32 tests, frontend 17 tests, AI/parser 15 tests passed. Full `make test` and Compose boundaries passed.
- [x] `make up`, core/Phase 1/Phase 2 smoke regressions, and Phase 3 live smoke passed, including real two-page PDF/TXT extraction, byte-identical download, IDOR denial, proxy/API size limits and file cleanup.
- [x] OpenAPI 0.4.0 document routes and multipart/metadata/413 schema verified; README, API examples, threat model, synthetic PDF generator and evidence updated.
- [ ] Browser visual/click E2E still blocked: browser access host unavailable. Component server-rendering and live HTTP results are recorded separately.

Evidence: `docs/evidence/phase-3.md`. No vector chunk/embedding, READY indexing, Gemini answer, malware scan, OCR, browser E2E, or production deployment result is claimed. A crash before upload metadata commit can leave inaccessible orphan files; automatic orphan reconciliation is not implemented. Cleanup/replay/parser capacity target a single-instance local demo. Existing project-date PATCH and login/proxy limits remain as previously documented.

Phase 4 secure RAG is implemented below.


## Phase 4 — Secure RAG (completed 2026-11-07)

- [x] Common Flyway V4 chunk/model/dimension/scoped FK schema; PostgreSQL V5 mandatory vector(768), exact-search indexes only.
- [x] Page-preserving 1200-character chunks with 200 overlap; normalized, validated 768-dimension Gemini embeddings in bounded batches.
- [x] Configurable real Gemini REST generation adapter, structured sources, bounded output/timeouts and sanitized errors/429s; keys stay internal.
- [x] SQL trusted-project + READY + model/dimension restrictions before cosine ORDER BY/top-6; conservative relevance cutoff; insufficient context skips generation.
- [x] Spring AI status/ask/index authorization, viewer index denial, project locking and scoped citation validation; HMAC binds all private operations.
- [x] Automatic index attempt after configured upload, explicit indexing/reindex for extracted documents, atomic chunk/READY commit, cascading chunk deletion.
- [x] Project AI panel with independent questions, examples, memory-only answers, escaped answer text, citation downloads, index controls and no-key help.
- [x] No-key normal stack observed: 503 AI_NOT_CONFIGURED; projects/dashboard/documents remain functional, no provider call.
- [x] Full isolated `make proof-rag` passed: real HTTP/pgvector with closer foreign chunks, READY/model/vector/FK checks, citations/download, two-user and prompt scope, viewer/revocation and deletion. Mock provider exists only in the test image. Disposable proof stack/volumes cleaned up.
- [x] Backend 35 tests, frontend 21 tests, AI 28 tests passed; builds, Compose checks and earlier-phase live smoke regressions passed.
- [x] OpenAPI 0.5.0 and README/API/threat/evidence updated; dependency/model choices referenced to official sources.
- [ ] Browser visual/click E2E unavailable: browser access host unavailable.
- [ ] No real Gemini call/account/answer-quality validation performed; adapter tested with mock HTTP and full stack with test-only provider. Relevance threshold remains conservative and uncalibrated.

Evidence: `docs/evidence/phase-4.md`. The initial isolated proof encountered an occupied backend host port; proof now publishes only its frontend (5174). Combined live smoke hit the normal login rate limit; the no-key smoke retries once after the next window without weakening controls. Runtime/provider DB grants and distributed usage/replay limits remain documented production work.

Next explicit milestone: **Phase 5 — DevSecOps, proof and polish**: CI pipeline/security scans, browser E2E where available, final UX/docs/architecture/demo evidence, and explicit blockers for unavailable external tooling. Current local stack remains usable without a Gemini key.


## Phase 4 amendment — Local Ollama Qwen RAG (completed 2026-11-07)

The owner requested local inference using their existing `qwen3:4b` and `qwen3-embedding:0.6b` installation instead of cloud Gemini. This changes the provider choice within Phase 4; Phase 5 has not started.

- [x] Added native Ollama embedding/chat adapter and explicit `AI_PROVIDER` selection; Gemini remains available. Unknown providers fail closed.
- [x] Host gateway resolution for Docker-to-host Ollama, ignored local `.env` configured for the installed models; no host listener/firewall changes or model downloads.
- [x] Live Qwen embeddings confirmed at 768 dimensions; normalize/check dimensions, count, finite/nonzero values; provider-qualified vector model metadata prevents cross-provider mixing.
- [x] Structured JSON answer/source generation, thinking disabled, bounded context/output, no tools, sanitized errors/timeouts; SQL/project/READY and Spring source validation preserved.
- [x] Longer bounded local inference timeouts, provider-neutral UI/backend guidance, documented setup, spec owner amendment and local trust boundary.
- [x] 19 new adapter/security cases; AI total 47 passing tests, one existing Starlette test-client deprecation warning.
- [x] `make test` passed: backend 35 tests, frontend lint/tests/production build stages, AI 47-test image and Compose boundaries. Initial sandbox Docker access denial was resolved by approved execution; no failed build remains.
- [x] `docker compose up --build --wait --wait-timeout 240` passed; all four services healthy. `make smoke`, `make config`, `git diff --check` passed. `.env` and demo credentials remain Git-ignored.
- [x] `python3 scripts/ollama-smoke.py --keep-demo` passed with actual local inference: upload automatically READY, answer with Maya and 12 November 2026, scoped citations/byte-identical download, outsider/revoked-member denials and viewer indexing denial. Initial smoke assertion compared normalized excerpts against unnormalized whitespace; corrected expectation, rerun passed and initial temporary project cleaned up.
- [x] Left a synthetic **Local Qwen RAG demo** project for `manager@demo.local`, containing a fictional meeting TXT and real indexed vectors. Ask: “Who owns the safety review, and when is it due?”

Changed files for this amendment: `ai-service/app/provider.py`, `ai-service/app/rag.py`, `ai-service/tests/test_ollama.py`, `compose.yaml`, `.env.example`, ignored `.env`, `backend/src/main/java/dev/structiq/ai/InternalAiClient.java`, `frontend/nginx.conf`, `frontend/src/features/ai/Assistant.tsx`, `scripts/ollama-smoke.py`, `scripts/phase4-smoke.py`, `Makefile`, `PROJECT_SPEC.md`, `README.md`, `docs/threat-model.md`, and this progress note.

Remaining: broader Qwen answer/retrieval quality and cutoff calibration, cold-start/large-document performance, browser visual/E2E verification, and existing production limitations. Configured status means the Ollama adapter is selected; request errors indicate server/model unavailability. Reindex prior Gemini documents to make them searchable under Ollama. No Gemini request or account validation is claimed. Next milestone remains Phase 5 on explicit instruction.


## UX and showcase improvement (2026-11-07)

Requested by the owner after trying the local Qwen demo. This is the UX portion of Phase 5; CI/security scans and final browser proof are not complete.

Implemented:
- Redesigned login, sidebar/header, project cards, forms and responsive workspace styling, with local vector icons and reduced-motion support. No external font/image dependency or invented dashboard metrics.
- Delivery overview with real task-completion ring, accessible metric labels, project progress, overdue attention list and upcoming work. Project links can open the assistant/tasks directly.
- URL-addressable project Overview/Tasks/Documents/AI assistant/Team sections, replacing the long stacked page. Direct destinations survive normal sign-in; auth remains in memory.
- Board/List task switch over the same filtered/paginated API records. Board columns group the current page only (up to 12 tasks, explicitly labeled). Existing status mutations and manager/member/viewer controls remain in use; no simulated drag-and-drop.
- Focused assistant with briefing/action/deadline/blocker prompts, editable questions, Ctrl/Cmd+Enter, pending state, clear in-memory conversation, indexed-document empty state, source library, and authorized citation downloads. The sidebar shows only the first 12 document metadata records and labels pagination.
- Safe basic answer emphasis/list formatting through React text nodes; raw HTML and model-supplied links never execute. Questions still use independent RAG requests.
- Opt-in `make showcase` / `scripts/seed-showcase.py`: additive, rerunnable **Riverside Delivery Lab**, three synthetic indexed documents and eight persisted tasks, relative dates, real provider-generated blocker answer. Existing projects and edits are preserved.

Modified files: `frontend/src/main.tsx`, `frontend/src/styles.css`, new `frontend/src/components/Icon.tsx`, `frontend/src/features/work/Dashboard.tsx`, `frontend/src/features/work/Tasks.tsx`, `frontend/src/features/work/work.test.tsx`, `frontend/src/features/ai/Assistant.tsx`, `frontend/src/features/ai/Assistant.test.tsx`, new `scripts/seed-showcase.py`, `Makefile`, `README.md`, and this progress record.

Observed checks:
- `docker build --target test -t structiq-frontend-test frontend`: final TypeScript lint, **24 tests**, and production build passed. Initial run found three stale rendering fixtures (missing Router context and old metric markup); corrected fixtures and rerun passed.
- Added/updated safety/behavior checks for escaped HTML/links in formatted answers, empty-library disabled question submission, board member permissions, actual dashboard counts/progress, and assistant/task links. Existing API/document permission component tests still pass.
- `docker compose up --build --wait --wait-timeout 240 frontend`: passed, final frontend rebuilt after deep-link sign-in improvement; all four services healthy. It also reran all 24 frontend tests, TypeScript checks and production build successfully.
- `python3 scripts/seed-showcase.py`: passed actual local Qwen embedding/generation, three READY sources/eight tasks, supplier blocker and prerequisite answer with citations belonging to this project. No cloud call or fake response.
- `make smoke`: passed web/proxied API/readiness/private AI/pgvector and unauthenticated 401 checks after approved local socket access. Initial restricted sandbox invocation could not open local sockets; approved rerun passed.
- `make config`, `git diff --check`: passed.
- CUA browser inventory returned “The browser access host is unavailable.” No visual screenshot, browser click result, mobile screenshot, or E2E pass is claimed. Responsive behavior is implemented in CSS but still needs visual confirmation.

Try now: refresh http://localhost:5173, sign in as `manager@demo.local` using the existing generated password, and open **Riverside Delivery Lab**. Explore Tasks → Board/List, Documents, then AI assistant. Ask: “What is blocking the fire-door installation, who owns the supplier follow-up, and what must happen before installation starts?” Or choose Give me the briefing and send the prepared question. Recreate from a reset with `make up`, `make seed`, configure local Ollama as documented, then `make showcase`.

Remaining: browser visual/click verification, representative answer/retrieval benchmarking and previously recorded production limits. Next Phase 5 work is CI/DevSecOps/security scans and final E2E proof; it is not marked complete here. No commit was made over the pre-existing uncommitted phase changes.

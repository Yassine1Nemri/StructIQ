# Phase 3 verification — 2026-11-07

Executed in the existing Ubuntu workspace with Docker Engine 29.9.0 / Compose v5.6.0. No clean-clone or browser result is claimed.

| Command / check | Observed result |
| --- | --- |
| Backend test image build | Exit 0; Java 21/Maven verify, 32 tests, no failures/errors/skips |
| Frontend test stage in `make up` | TypeScript lint, 17 Vitest tests across 3 files, and production build passed |
| AI test image / `make test` | 15 pytest cases passed, 1 Starlette HTTPX deprecation warning |
| `make test` | Exit 0; backend/frontend/AI test targets and Compose boundary checks passed |
| `make up` | Exit 0; all four services healthy; Flyway V3 applied to PostgreSQL; private volume provisioned |
| `make smoke`, `make smoke-phase1`, `make smoke-phase2` | Exit 0; health/auth/project/task/dashboard regressions passed |
| `make smoke-phase3` | Exit 0; actual PDF/TXT extraction via signed Spring → FastAPI call; download/isolation/deletion/size checks passed |
| Final `make smoke-phase3` after nginx size-response change | Exit 0; both 10 MiB API-file limit and 11 MiB proxy-envelope limit returned JSON FILE_TOO_LARGE |
| Generated OpenAPI inspection | Version 0.4.0; document list uses distinct DocumentPage schema; multipart/form-data upload; metadata has no storage key/checksum/text; download/retry paths and 413 response exist |
| Runtime `id` in backend / AI | Backend UID/GID 10001; AI UID 10002, supplementary group 10001 |
| Compose boundary assertions | AI/DB private; web/API loopback; shared secret configured only for trusted services; parser document mount read-only; nginx has no private volume |
| Python compile / ignored-secret checks | Scripts/app modules compiled; `.env` and `.demo-credentials.json` ignored |
| Browser inventory | No surfaces; `The browser access host is unavailable`; visual/click E2E blocked |

Seven new backend HTTP/security tests cover: scoped metadata and byte downloads; nonmember/unauthenticated denial; document/project ID mismatch even when the caller manages both projects; viewer and uploader deletion rules; immediate revocation; unsafe/mismatched/empty/binary/invalid-UTF-8/oversized uploads; filename traversal and duplicate-name isolation; failure/retry without claiming vector readiness; document/project deletion of bytes and extracted pages; durable cleanup during simulated storage failure. The Java suite mocks the parser response and uses the real Spring HTTP server/repositories with Flyway on H2 PostgreSQL mode. Its prior task/project/JWT tests continue to pass.

AI tests execute the real bounded parser subprocess for TXT and hostile-instruction text and directly verify multi-page PDF extraction, encryption/scanned/corrupt failures and input signatures. Authentication tests reject missing/tampered/expired/future/replayed requests, arbitrary storage-key fields, mismatched project paths and symlinks. Text-limit and simulated timeout responses are sanitized. Invalid-text parametrization runs six cases; total is 15 including the three existing health tests. No model/provider calls are made.

Frontend adds two request tests (multipart boundary handling with bearer token; private binary download) and three server-rendering tests (viewer controls, member-own vs manager deletion, extraction/AI-pending and scanned-PDF retry messages). Synthetic cache fixtures are used only in tests; these are not browser interaction tests.

The live Phase 3 script uses generated synthetic accounts and disposable projects. It uploads a valid two-page PDF and TXT through nginx/Spring, invokes the real HMAC parser against the read-only mounted volume, verifies extracted-page counts and exact downloaded-byte hashes, tests unauthorized and mismatched-ID access, checks bad file types and both size limits, confirms scanned-PDF failure/retry, revokes membership, and verifies private files are gone after document/project deletion. It removes its temporary data and prints no credentials, extracted text, or storage keys.

Storage provisioning was adjusted before runtime validation so backend directories/files inherit GID 10001, which the read-only parser can access. Python test cleanup removed a redundant empty-regex warning; one dependency deprecation warning remains. The existing H2/Flyway and Java test-agent/deprecated API warnings also remain. All recorded final checks passed; no scan, Jenkins, paid API, Testcontainers or browser E2E result is fabricated.

Limitations: READY/vector chunks/embeddings/citations await Phase 4. Extracted pages are private ingestion scaffolding, not indexed content. OCR and malware scanning are not implemented. Parser limits reduce resource exhaustion without proving every malicious PDF is safe. A hard crash before upload metadata commit can leave inaccessible orphan bytes; no automatic orphan reconciliation is implemented. Cleanup, replay state and parser concurrency are for the single-instance demo.

Modified groups: backend document module/V3 schema, project deletion integration, error/status/OpenAPI configuration and security tests; FastAPI authenticated extraction/worker/parser/tests and pypdf dependency; frontend typed upload/download client, Documents panel/tests/styles; Dockerfiles/Compose/private volume/nginx limits; Makefile, smoke/PDF-fixture scripts, environment example, README/API/threat/progress/evidence docs. No commit was created by this continuation; repository state changed from the initially untracked scaffold during the session.

The stack remains running at http://localhost:5173. Generate a fictional upload with `python3 scripts/demo_documents.py`; sign in using the ignored local credentials file and upload `/tmp/structiq-fictional-meeting.pdf` in a project. No passwords or private document contents are included in this evidence.

# Phase 0 verification — 2026-11-07

Observed in this workspace on Ubuntu using Docker Engine 29.9.0 and Compose v5.6.0. This is an existing working directory verification, not a separate fresh-clone test.

| Command / check | Observed result |
| --- | --- |
| `python3 scripts/init-env.py` | Created local `.env`, mode 0600, without printing values |
| `docker compose config --quiet` | Exit 0 |
| `python3 scripts/check-compose.py` | Exit 0: four services with healthchecks; private AI/DB; loopback web/API ports |
| `docker build --target test -t structiq-backend-test backend` | Exit 0, Maven verify succeeded |
| Backend Surefire report read from test image | 5 tests, 0 failures, 0 errors, 0 skipped |
| `docker build --target test -t structiq-ai-test ai-service` | Exit 0, 3 tests passed, 1 Starlette HTTPX deprecation warning |
| `docker build --target test -t structiq-frontend-test frontend` | Exit 0, TypeScript lint passed, 3 Vitest tests passed, Vite production build succeeded |
| `make test` | Exit 0, all three test images built and Compose boundary assertions passed |
| `make up` | Exit 0 after fixes; all four runtime containers healthy |
| `docker compose ps` | AI, backend, frontend, PostgreSQL healthy; only 127.0.0.1:5173 and 127.0.0.1:8080 published |
| `make smoke` | Exit 0: web HTML, proxied status, API health/readiness, internal AI health, pgvector 0.8.2, protected route 401 |
| `python3 -m py_compile scripts/*.py ai-service/app/*.py` | Exit 0 |
| `git check-ignore .env` | `.env` is ignored |
| Browser interface check | Blocked: computer-use tool reported `The browser access host is unavailable`; no visual QA or screenshots claimed |

Security coverage executed: unauthenticated project request returns 401 with `nosniff`; sensitive actuator endpoint returns 401; untrusted CORS origin returns 403; liveness exposes no component details; AI health does not disclose a test provider key; unsupported AI/docs routes return 404. Compose assertions verify private AI/DB and loopback-only host ports. No membership, JWT, upload, or RAG isolation claim is made at this phase.

Failures resolved during continuation:

- Frontend Dockerfile required a missing package lock. Host npm 10.8.2 crashed in Arborist (`edgesOut`); npm 11.21.2 generated the lock successfully. Its Node 20 engine warning led to an explicit supported Node engine declaration; builds and tests ran on Node 24.
- TypeScript 7 rejected the CSS side-effect import without Vite declarations. Added `src/vite-env.d.ts`; frontend checks then passed.
- The first startup used the pre-fix frontend build context and failed. Rebuilding after the declaration fix succeeded.
- Smoke verification initially expected the root health JSON to contain only `status`. Spring also returns public `groups` names. Updated the check to require `UP` and permit only `status` / `groups`, excluding component details; rerun passed.
- Initial sandbox Docker/network access failures required approved execution outside the sandbox. No service or package results were invented.

Remaining limitations: browser visual QA unavailable; no separate clean-clone run; runtime base images use tags, and Python transitive dependencies are not locked. The three AI tests emit a dependency deprecation warning. Full vulnerability scans, E2E browser tests, SonarQube analysis, and Jenkins execution have not been run and belong to later phases.

The running stack has been left available at http://localhost:5173. Stop with `make down`; database volumes persist.

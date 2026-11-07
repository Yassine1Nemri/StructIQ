# Phase 1 verification — 2026-11-07

Executed in the existing Ubuntu workspace with Docker Engine 29.9.0 / Compose v5.6.0. No separate clean-clone test or browser rendering result is claimed.

| Command / check | Actual result |
| --- | --- |
| `docker build --target test -t structiq-backend-test backend` | Final run exit 0; Java 21, Maven verify, 17 tests, 0 failures/errors/skips |
| `docker build --target test -t structiq-frontend-test frontend` / frontend stage in `make up` | Exit 0 after fixture fix; TypeScript lint, Vitest, and production build passed |
| `docker run --rm structiq-frontend-test npm test` | Exit 0; 6 client tests passed |
| `make test` | Exit 0; backend/frontend/AI test image targets and Compose boundary checks passed; unchanged AI stage has 3 passing tests |
| `make up` | Exit 0; four healthy services; Flyway V1 applied successfully to PostgreSQL 17.10 |
| `make seed` | Exit 0; four synthetic users and two projects; generated password saved privately without printing it |
| `make smoke` | Exit 0; web HTML, proxied status, API readiness, private AI health, pgvector 0.8.2, protected API 401 |
| `make smoke-phase1` | Exit 0; live PostgreSQL API checks through nginx passed and temporary verification project deleted |
| `docker compose exec -T backend curl --fail --silent http://localhost:8080/v3/api-docs` | Exit 0; generated OpenAPI 3.1 schema with project/member/auth paths, DTO schemas, bearer scheme and error examples |
| `python3 -m py_compile scripts/*.py ai-service/app/*.py` | Exit 0 |
| `git check-ignore .env .demo-credentials.json` | Both secret files ignored |
| `git diff --check` | Exit 0 (repository files remain untracked from initial scaffold; no commit claimed) |
| Browser inventory | No browsers; tool reported `The browser access host is unavailable` |

Backend test breakdown: 9 project/auth HTTP integration tests, 1 HTTP login-limit test, 1 limiter clock unit test, 6 foundation/security tests. The integration tests run the real Spring HTTP server and repositories with the same Flyway V1 migration on H2 PostgreSQL mode. PostgreSQL is additionally checked by the live smoke test; these are not Testcontainers tests.

Security proof added in this phase:

- Project list/detail/member reads and mutations remain isolated between two users/projects; member IDs cannot be moved across project paths.
- Member cannot create projects, edit/delete projects, or escalate membership, even when a test-signed JWT asserts ADMIN.
- Actual database ADMIN can access other projects and bootstrap; removing that database role changes permissions immediately with the same JWT.
- Removing project membership immediately revokes access; MEMBER and VIEWER manager mutations are denied.
- Last manager cannot be demoted/removed; creator membership and project deletion cleanup persist correctly.
- Tampered, malformed, expired, wrong-issuer, wrong-audience, invalid-subject, and missing-expiry JWTs are rejected. A deleted user cannot continue using `/auth/me`.
- Login uses BCrypt and returns a usable 900-second bearer session; known/unknown accounts receive the same invalid-credential message.
- Unit tests prove per-address limiting and window expiry; HTTP test proves 429 with `Retry-After` and correlation ID.
- Malformed JSON/UUIDs, invalid states, blank names, invalid date ranges, and pagination limits produce sanitized errors.
- API docs are blocked by default; private actuator access and untrusted CORS rejection remain covered.
- Frontend tests verify Authorization bearer handling, cookie omission, token clearing, expiry callback, and retaining a session after project permission denial.

Live smoke separately verified login/current-user, unauthenticated denial, manager creation/edit/filter/delete, automatic creator membership, admin access, outsider denial, member self-escalation denial, viewer restrictions, revocation, last-manager conflict, and cleanup through nginx and PostgreSQL. It does not exercise UI clicks.

Issues resolved: the initial frontend test mock reused one consumed `Response`; changed it to return a fresh response per request. Search's nullable status parameter uses an explicit SQL cast for PostgreSQL parameter inference. Maven uses a BuildKit dependency cache to avoid repeated downloads. No successful UI, provider, scan, Jenkins, or E2E call was fabricated.

Observed warnings: H2 2.4.240 is newer than Flyway's verified H2 2.3.232; tests still passed. Java test tooling reports dynamic Mockito/Byte Buddy agent warnings and deprecated API use in tests. The unchanged AI test image previously reported a Starlette HTTPX deprecation warning. These are warnings, not failed checks.

Remaining checks: browser UI QA/screenshots and browser E2E are blocked/unrun. Production deployment, distributed login limiting, vulnerability scans and Jenkins execution are outside this phase. JWT logout is client-side; stolen tokens expire after 15 minutes and resource access remains subject to current database permissions. Clearing existing project dates via PATCH is not implemented. Tasks, documents, dashboard metrics, and RAG remain later phases.

The local stack is left running at http://localhost:5173. Demo accounts: `admin@demo.local`, `manager@demo.local`, `engineer@demo.local`, `outsider@demo.local`; read the ignored `.demo-credentials.json` locally for the generated password. No passwords or access tokens are included in this evidence.

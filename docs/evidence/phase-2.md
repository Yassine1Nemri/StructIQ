# Phase 2 verification — 2026-11-07

Executed in the existing Ubuntu workspace using Docker Engine 29.9.0 / Compose v5.6.0. No fresh-clone or browser execution is claimed.

| Command / check | Actual result |
| --- | --- |
| `docker build --target test -t structiq-backend-test backend` | Exit 0 after deletion-order fix; Java 21/Maven verify, 25 tests, no failures/errors/skips |
| Frontend test stage (`docker build --target test -t structiq-frontend-test frontend`, then final stage in `make up`) | TypeScript lint, 12 Vitest tests across 2 files, and production build passed |
| `make test` | Exit 0; all backend/frontend/AI test image targets and Compose boundary checks passed; unchanged AI stage retains 3 passing tests |
| `make up` | Exit 0; PostgreSQL, backend, frontend, AI healthy; Flyway V2 applied to real PostgreSQL 17.10 |
| `make seed` | Exit 0; added five fictional task fixtures and preserved local generated credentials |
| `make smoke` | Exit 0; web, proxy, readiness, private AI health, pgvector 0.8.2, unauthenticated project denial |
| `make smoke-phase1` | Exit 0; authentication/project/membership regression passed through nginx |
| `make smoke-phase2` | Exit 0; PostgreSQL-backed task policy and dashboard-delta checks through nginx; temporary projects cleaned up |
| Generated OpenAPI inspection | Version 0.3.0 / OpenAPI 3.1.0; task POST references TaskCreate; My Tasks and summary paths exist; schema has all dashboard fields; 400 example uses INVALID_INPUT |
| `python3 -m py_compile scripts/*.py ai-service/app/*.py` | Exit 0 |
| `git check-ignore .env .demo-credentials.json` | Both secret files ignored |
| Browser inventory | No browsers/apps; `The browser access host is unavailable`; visual/click verification blocked |

Eight new backend HTTP integration tests prove:

- Other-project task listing, creation and mutation are denied; guessed task IDs cannot be used with a different project even when the caller manages both.
- MEMBER can move only their assigned task's status among known states. Changes to title/description/assignment/priority/deadline, including extra null fields, are denied. VIEWER cannot change assigned task status.
- Assignee outside the path project is rejected for manager and admin; a direct SQL bypass is rejected by the composite foreign key.
- Managers can edit/reassign, explicitly clear assignee/deadline, preserve omitted fields, and delete tasks.
- Removing membership unassigns tasks, clears My Tasks results, and immediately denies further access.
- Illegal enums, blank/oversized/null titles, malformed dates, unknown PATCH fields, empty PATCH and invalid filters are rejected.
- Status/priority/assignee/deadline filters, pagination, UTC overdue semantics and My Tasks use persisted rows.
- Dashboard counts, status groups and project progress change after member completion without exposing the second project's rows; admin totals work; deletion cleans tasks.

Frontend added three request tests and three component-rendering tests. They verify status-only member payloads, explicit-null preservation, My Tasks query construction, member/viewer/manager controls, and data-driven dashboard counts/progress/deadline links. Component tests use React server rendering with synthetic Query cache fixtures; they are not browser E2E tests. Backend integration uses the real Spring HTTP server and Flyway migrations with H2 PostgreSQL mode; the live smoke separately covers actual PostgreSQL.

The live Phase 2 smoke records before/after metrics, creates isolated disposable manager/outsider projects, checks ID mismatch and foreign assignment rejection, completes a task as the assignee, observes reduced open/overdue counts and 100% progress, clears task fields, tests membership removal/unassignment, and deletes the disposable projects. It preserves seeded projects/tasks and prints no tokens/passwords.

Failure fixed: the first expanded backend suite had one failure when project deletion encountered the task → assignee membership foreign key during cascading cleanup. Project deletion now explicitly removes tasks before deleting the project and memberships, inside the existing locked transaction. The test asserts 204 and verifies task cleanup; the final 25-test suite and live PostgreSQL deletion passed.

Warnings remain from existing test tooling: H2 version newer than Flyway's verified version, Java dynamic test agent/deprecated test API notices, and the unchanged AI Starlette/HTTPX deprecation warning. No vulnerability scans, provider requests, Jenkins execution, Testcontainers, or browser E2E result is claimed. Browser QA is blocked; concurrent edits use last-writer-wins, and large-scale query performance has not been measured.

Changed files: backend task/dashboard modules, V2 migration, scoped project repository/service/DTO updates, demo seed, API security/status/OpenAPI configuration and tests; frontend task/dashboard components, shared feedback, typed client, navigation/styles and tests; Makefile and Phase 2 smoke; README, API examples, threat model and progress/evidence. The repository still contains the pre-existing untracked initial scaffold; no commit is claimed.

The stack is left running at http://localhost:5173. Read the ignored `.demo-credentials.json` locally for the demo password; use manager then engineer to demonstrate assignment → own-status update → dashboard change. Stop with `make down` to preserve database volumes.

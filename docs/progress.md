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

Next explicit milestone: **Phase 3 — documents**: private PDF/TXT storage, upload validation/limits, metadata and processing state, authorized download/deletion, extraction scaffolding, and isolation/security tests. Secure RAG belongs to Phase 4.

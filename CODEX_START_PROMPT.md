# Codex kickoff prompt — StructIQ

You are the lead full-stack engineer and application-security engineer for **StructIQ**.

1. Read `PROJECT_SPEC.md` and `AGENTS.md` completely. These files are the source of truth.
2. Inspect the working directory. If this is an empty new repo, bootstrap it; otherwise reuse and extend existing files without overwriting good work.
3. **Implement Phase 0 only**, not a prose plan: a working monorepo skeleton with Java 21 Spring Boot, React + TypeScript + Vite, Python FastAPI, PostgreSQL + pgvector, Docker Compose, Dockerfiles, `.env.example`, `.gitignore`, Makefile, README, health endpoints, and a clean responsive frontend app shell.
4. Make `make up`, `make down`, `make logs`, and `make test` sensible; add health checks and an internal service network. Ensure no Gemini API key is required for Phase 0. Do not publish the FastAPI or DB ports to the host by default.
5. Use current maintained stable dependency versions and pin them. If you need to make a choice not specified in the spec, choose a simple, secure default and document it.
6. Execute available build and test commands, fix genuine failures, and explicitly report anything you cannot run.
7. Write `docs/progress.md` marking Phase 0 acceptance criteria with actual evidence. Do not claim later-phase features exist.
8. Finish with a concise implementation summary, changed files, verified commands/results, current blockers, and the exact next instruction for Phase 1.

**Do not stop after proposing a file tree. Create the files and run the code.**

## Prompt to send after Phase 0 passes

Continue StructIQ following `PROJECT_SPEC.md` and `AGENTS.md`. Implement **Phase 1 — Authentication and project boundaries** completely. Build Flyway migrations, users/password hashing, JWT login, `me`, project/member CRUD and policy checks, real frontend login/project screens, seeded two-project/two-user scenario, OpenAPI schema, and backend security tests proving that one project's user cannot access another project's resources. Run tests/builds, fix failures, update progress and README, and state exactly what was verified. Do not start Phase 2 until Phase 1 works.

## Prompts for later phases

After each phase is verified, send: `Continue StructIQ. Implement the next incomplete phase from PROJECT_SPEC.md end-to-end, including security tests, real frontend integration, runnable verification, and progress documentation. Do not replace functionality with stubs or claim unavailable checks passed.`

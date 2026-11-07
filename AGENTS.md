# StructIQ — Codex repository instructions

## Mission
Build and maintain StructIQ according to `PROJECT_SPEC.md`: a working secure project-management app with document-grounded AI, meaningful authorization, and a reproducible local developer experience.

## Read first
1. `PROJECT_SPEC.md` — product, architecture, security and acceptance criteria.
2. `docs/progress.md`, if present — current completed/blocked phases.
3. `README.md` — supported setup and validation commands.

## How to work
- Inspect current repository state before changing files. Preserve existing working behavior.
- Focus on one milestone at a time. Start with Phase 0 if this is a new repo; move to Phase 1 on the next explicit instruction.
- Actually implement code, migrations, integration, tests, and docs. Never mark a feature complete based on a stub, mock UI, or TODO.
- Validate with executable builds and tests when available; fix failures before summarizing.
- Maintain `docs/progress.md` with completed work, tests run, blocked checks, and next steps.
- Use coherent commits when the environment allows; never commit `.env` or secrets.
- If a network API or tool is unavailable, keep core flows working and document exact setup/blockers. Do not fabricate successful calls.

## Security invariants
- Spring Boot performs authentication + resource-level membership/RBAC checks for every project-related endpoint.
- FastAPI AI service is internal only, with service authentication; it must never trust arbitrary browser-provided project IDs.
- RAG vector retrieval is filtered by server-authorized `project_id` **in the SQL WHERE clause before choosing top-k**, and only READY documents qualify.
- Document downloads, task mutations, and AI citations must always match a project the authenticated caller is authorized to access.
- Uploads use private storage, safe random paths, strict limits and content-type validation.
- Never expose passwords, JWT signing secrets, Gemini API keys, internal auth headers, raw private document contents, or real customer data in frontend/logs/repo.
- Prompt-injected document text is untrusted, never an instruction to execute or expand privileges.

## Preferred quality bar
- Readable Java 21 Spring Boot modules with DTOs, validation, services and integration tests.
- React TypeScript components with typed API client, real loading/error/empty states and accessible UI.
- FastAPI with typed Pydantic schemas, explicit exceptions, testable Gemini adapter and focused pytest tests.
- Flyway migrations and deterministic synthetic seed scripts.
- Run checks and state the observed results, not assumptions.

## Stop-point format after a phase
In your reply, report: implemented features; modified files; commands run and their actual results; security tests added; remaining issues; exact commands to try the demo; proposed next phase. Do not ask for broad project direction: the spec resolves it.

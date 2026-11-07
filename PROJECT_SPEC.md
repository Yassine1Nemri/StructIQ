# StructIQ — Secure Project Management + AI Document Assistant

> **Status:** Implementation specification for Codex. This is a portfolio project inspired by real business needs, **not** a copy of any company's proprietary product.
>
> **Goal:** Build a genuinely functional, locally runnable full-stack web app demonstrating software engineering, application security, RAG, and DevSecOps. Prefer clean, tested working features over impressive-looking placeholders.

## 1. Product vision

A small construction, design, or consulting company can manage multiple projects, assign tasks, store meeting reports and project documents, monitor deadlines, and ask an AI assistant grounded questions such as:

> "According to the latest meeting report, which tasks remain unfinished and who owns them?"

The assistant answers from **only documents the current user is authorized to read**, citing document title and page/section. It must say when information is unavailable rather than fabricate an answer.

**Brand:** StructIQ. Modern B2B SaaS appearance, calm professional colors, responsive, accessible, and easy to demo. Use only synthetic demonstration data.

## 2. Non-negotiable engineering principles

1. Implement real frontend-to-backend-to-database flows. **No fake dashboards, hardcoded results, or mock APIs in the final app.** Fixtures/mocks are allowed in tests only.
2. Work incrementally. Each phase must have working code, tests, and clear verification instructions before starting the next.
3. Enforce authorization **on the backend and before RAG retrieval**. Hiding UI buttons is not authorization.
4. Never send documents from unauthorized projects to the LLM, embeddings provider, or frontend.
5. Do not commit real credentials, `.env`, API keys, JWT secrets, private uploaded documents, or production data.
6. Keep local setup practical on an Ubuntu computer with around 24 GB RAM. Only the core app should start by default; heavy developer tools belong behind optional Docker Compose profiles.
7. Prefer understandable modular architecture over unnecessary microservices, Kubernetes, queues, or abstractions.
8. Validate user input, handle error states, and use useful logs without leaking content or secrets.
9. If an API key is absent, the normal app must still run; AI endpoints return a clear `AI_NOT_CONFIGURED` response and the UI explains how to enable AI.
10. **Do not claim a security control is implemented until a test proves it.**

## 3. Stack and decisions

| Layer | Technology | Notes |
|---|---|---|
| Web app | React + TypeScript + Vite | React Router, TanStack Query, typed API client |
| UI | Tailwind CSS + reusable accessible components | Clean desktop-first dashboard, mobile responsive |
| API | Java 21 + Spring Boot + Spring Security | REST, Bean Validation, Spring Data JPA |
| Authentication | JWT Bearer access token | Short-lived, kept in frontend memory (not localStorage); re-login on page refresh is acceptable for MVP |
| Database | PostgreSQL + pgvector | Single database for business data and document embeddings |
| Schema migrations | Flyway | Versioned SQL; do not use Hibernate auto-update in release |
| AI service | Python 3.12 + FastAPI | Internal-only document parsing, embeddings, retrieval, generation |
| Model provider | Gemini API | Configurable generation model; `gemini-embedding-001` for text embeddings, request 768 dimensions |
| Document parsing | pypdf and text/plain | PDF + TXT MVP; DOCX is an optional stretch goal |
| Tests | JUnit 5, MockMvc, Testcontainers, pytest, Vitest, Playwright | Security isolation tests required |
| Local run | Docker Compose | Postgres, API, AI, frontend; named volumes and health checks |
| CI/CD | Jenkinsfile + Docker + SonarQube integration | Quality, tests, security scanning, image build; optional services for local demos |
| API docs | OpenAPI / Swagger UI | Real endpoint schemas and error examples |

Choose compatible **maintained stable patch versions** at implementation time; pin dependencies and document versions in `README.md`. Avoid deprecated Gemini model IDs. Put embedding model and output dimension into configuration; embedding dimension must agree with pgvector schema and indexes. Do not silently mix embeddings from different models.

## 4. Architecture

```text
Browser (React / TypeScript)
          |
          | HTTPS in production / HTTP localhost
          v
Spring Boot REST API  <---->  PostgreSQL (+ pgvector)
  |   |      |
  |   |      +-- users / projects / memberships / tasks / documents
  |   |                                  +-- document_chunks vectors
  |   +-- Private document volume (no public static serving)
  |
  +-- authorization + validation + JWT + audit events
  |
  +---- INTERNAL authenticated calls ----> FastAPI AI Service
                                          |  Parse PDF / TXT
                                          |  Chunk and embed
                                          |  Project-scoped vector search
                                          +----> Gemini API (when configured)
```

**Important trust boundary:** The browser calls **Spring Boot only**. FastAPI and PostgreSQL must have **no host-published ports by default** except local DB port if explicitly enabled for development. The API checks the current user's project membership and permission before calling FastAPI. The AI service additionally requires a short-lived signed internal request (HMAC with timestamp and nonce, or a narrowly scoped signed service token), verifies it, and always performs SQL retrieval filtered by the **authorized project ID** supplied by the trusted API. Never accept project IDs or arbitrary SQL authorization filters from the user's prompt.

AI indexing can run synchronously for small demo documents initially; expose an honest `PROCESSING` state and later add a durable queue if warranted. Avoid treating synchronous work as asynchronous unless it is implemented.

## 5. Roles and access rules

Use a **global** role `ADMIN` and **project membership** roles `MANAGER`, `MEMBER`, `VIEWER`. Each project has a set of member rows. Every project resource belongs to exactly one project. A platform admin may access any project but all non-admin access requires active membership.

| Operation | ADMIN | MANAGER (own project) | MEMBER (own project) | VIEWER (own project) |
|---|---|---|---|---|
| View project/dashboard | Yes | Yes | Yes | Yes |
| Create project | Yes | Yes | No | No |
| Edit/delete project | Yes | Yes | No | No |
| Manage project members | Yes | Yes | No | No |
| Create/assign/edit any task | Yes | Yes | No | No |
| Update own assigned task status | Yes | Yes | Yes | No |
| Read/download project docs | Yes | Yes | Yes | Yes |
| Upload documents | Yes | Yes | Yes | No |
| Ask RAG questions about project docs | Yes | Yes | Yes | Yes |

Decide whether a project creator automatically becomes `MANAGER` (recommended: yes). Platform ADMIN account is seeded for demo only, never with a fixed public password in a real deployment. Preserve task/project referential integrity and avoid exposing another project's users in assignment dropdowns.

**Critical:** `GET /api/projects/{projectId}/documents/{documentId}/download` must verify BOTH the project membership AND that the document belongs to `projectId`. Same for task IDs, updates, deletes, and AI citations. Unauthorized access should consistently respond with 403 or 404 without leaking resource metadata.

## 6. Functional requirements

### 6.1 Authentication and users
- Email + password login; BCrypt or Argon2 password hashing.
- Short-lived signed JWT including a trusted user identity and role, server-side validation of signature, expiry, issuer/audience as appropriate.
- `GET /api/auth/me`; logout clears in-memory token. A refresh-token flow is out of scope for MVP and must not be simulated.
- Login rate limiting; generic invalid-credential errors; server-side validation for all requests.
- Use Bearer tokens in `Authorization` headers; do not persist them in localStorage. Do not introduce cookie-based auth without implementing CSRF defenses.

### 6.2 Dashboard
- Total accessible projects, active projects, total assigned/open tasks, overdue tasks, upcoming deadlines.
- Project status and task completion charts from **real queries**.
- Recent updates feed is optional, but may be implemented using audit events.

### 6.3 Project management
- Create / list / view / update / archive or delete projects.
- Fields: name, description, status, start date, target date, created by.
- List filters, search, project progress derived from completed tasks.
- Membership management (add user by email, set project role, remove member).

### 6.4 Task management
- Fields: title, description, status (`TODO`, `IN_PROGRESS`, `BLOCKED`, `DONE`), priority (`LOW`, `MEDIUM`, `HIGH`), assignee, due date, timestamps.
- Create / list / filter / update / delete tasks.
- Project-specific list or simple Kanban board. Drag-and-drop is optional, but status changes must persist.
- Prevent assignment of a task to users outside that project.

### 6.5 Documents
- Upload PDF and TXT, initial limit 10 MiB per file, configurable.
- Check actual file type; use safe generated storage names; reject executable or dangerous formats; sanitize displayed filenames.
- Store files in private server-side volume; never use user-provided filename as a filesystem path.
- Metadata and processing status: `UPLOADED`, `PROCESSING`, `READY`, `FAILED`.
- View metadata, download with authorization, delete with cleanup of associated chunks.
- Extract PDF page numbers where available. PDF text extraction failure should be visible; scanned PDFs need not support OCR for MVP.

### 6.6 AI assistant (RAG; not a generic chatbot)
- Project-scoped chat panel with example prompts, loading/error/empty states, citations.
- Ingest approved document text -> normalize -> chunk with overlap -> Gemini embeddings -> pgvector storage.
- Embedding dimension **768**; keep configuration consistent across code and migration.
- On question: authorize project, embed question, perform **SQL retrieval filtered by authorized project ID** and only READY documents, then generate answer grounded on retrieved chunks.
- Retrieval example (conceptual; parameterized in code):

```sql
SELECT c.id, c.document_id, c.content, c.page_number,
       c.embedding <=> :query_embedding AS cosine_distance
FROM document_chunks c
JOIN documents d ON d.id = c.document_id
WHERE c.project_id = :authorized_project_id
  AND d.project_id = :authorized_project_id
  AND d.processing_status = 'READY'
ORDER BY c.embedding <=> :query_embedding
LIMIT :top_k;
```

- Never retrieve unfiltered global top-k and then filter in application memory.
- Return JSON with `answer`, `citations: [{documentId, filename, pageNumber, excerpt}]`, and optional metadata such as retrieval count. A citation must correspond to chunks actually retrieved and permitted.
- Treat document text as **untrusted data**. Prompt instructions inside PDFs cannot override system rules, authorize other projects, or trigger tools/actions. Do not provide the model with tools for arbitrary HTTP, command execution, file access, or DB access.
- If no relevant context is found, answer that the documents do not contain enough information. Add conservative retrieval relevance checks and clearly label that AI answers may need human verification.
- Never log complete document bodies, prompts with confidential content, model keys, or JWTs. Set provider usage/timeouts, handle network errors and 429s gracefully.
- The system's permission barrier must be independent of any LLM instruction/prompt.

## 7. Database schema (minimum)

Use UUID primary keys, meaningful constraints, and Flyway migrations.

```text
users
  id, email UNIQUE, password_hash, display_name, global_role, created_at
projects
  id, name, description, status, start_date, target_date,
  created_by -> users.id, created_at, updated_at
project_members
  id, project_id -> projects.id, user_id -> users.id, role,
  UNIQUE(project_id, user_id)
tasks
  id, project_id -> projects.id, title, description, status,
  priority, assignee_id -> users.id NULL, due_date,
  created_by -> users.id, created_at, updated_at
documents
  id, project_id -> projects.id, uploaded_by -> users.id,
  original_filename, storage_key, mime_type, size_bytes,
  checksum_sha256, processing_status, created_at
document_chunks
  id, project_id -> projects.id, document_id -> documents.id,
  chunk_index, page_number NULL, content, embedding vector(768),
  created_at
```

Create indexes on project ownership/filter fields, task due date/status, and `document_chunks(project_id, document_id)`. Begin with **exact project-filtered vector search** and only add an ANN index after measuring; approximate global ANN filtering can silently reduce matching results. Enforce deletion behavior explicitly in migrations/service logic. Never expose raw `password_hash`, `storage_key`, or embeddings in public response DTOs.

## 8. REST API contract (minimum)

Prefix `/api`:

```text
POST   /api/auth/login
GET    /api/auth/me

GET    /api/dashboard/summary

GET    /api/projects
POST   /api/projects
GET    /api/projects/{projectId}
PATCH  /api/projects/{projectId}
DELETE /api/projects/{projectId}
GET    /api/projects/{projectId}/members
POST   /api/projects/{projectId}/members
PATCH  /api/projects/{projectId}/members/{userId}
DELETE /api/projects/{projectId}/members/{userId}

GET    /api/projects/{projectId}/tasks
POST   /api/projects/{projectId}/tasks
PATCH  /api/projects/{projectId}/tasks/{taskId}
DELETE /api/projects/{projectId}/tasks/{taskId}

GET    /api/projects/{projectId}/documents
POST   /api/projects/{projectId}/documents    (multipart)
GET    /api/projects/{projectId}/documents/{documentId}/download
DELETE /api/projects/{projectId}/documents/{documentId}

POST   /api/projects/{projectId}/ai/ask
GET    /actuator/health (restrict sensitive actuator endpoints)
GET    /swagger-ui/index.html (development only or restrict in production)
```

Use pagination where appropriate. API response codes: 400 invalid input, 401 unauthenticated, 403 unauthorized, 404 not found, 409 conflict, 413 oversized upload, 429 rate limited, 503 AI unavailable. Use a consistent JSON error format with stable `code`, `message`, optional field-level validation details, and correlation ID. Document all endpoints with OpenAPI.

## 9. Frontend screens and interaction design

**Navigation:** Login -> authenticated app shell with sidebar: Overview, Projects, My Tasks, Documents (within project), AI Assistant (within project), Profile.

1. **Login:** email/password form, clear validation and failure feedback.
2. **Dashboard:** KPI cards, overdue task list, project progress, upcoming deadlines.
3. **Projects:** searchable cards/table, create/edit modal, detail view with members and progress.
4. **Tasks:** sortable/filterable task list or status board, create/edit drawer, assignment and due date controls.
5. **Documents:** drag-and-drop upload with size/type instructions, indexing status, download/delete.
6. **AI Assistant:** project-specific chat with citations clickable to authorized document download; explain when AI key is missing or documents have not finished processing.

**Design:** polished but not overdesigned. Desktop sidebar, accessible color contrast, good spacing/typography, skeleton loaders, empty states, toasts and confirmation dialogs. Responsive from 375px up. Every form must connect to functioning API endpoints, and every UI permission must mirror server-side policy (never replace it).

## 10. Repository layout

```text
structiq/
  AGENTS.md
  PROJECT_SPEC.md
  README.md
  .gitignore
  .env.example
  compose.yaml
  Makefile
  backend/
    pom.xml
    src/main/java/.../{auth,user,project,task,document,dashboard,ai,security,common}/
    src/main/resources/db/migration/
    src/test/
    Dockerfile
  frontend/
    package.json
    src/{app,components,features,lib,types}/
    tests/
    Dockerfile
  ai-service/
    pyproject.toml
    app/{main,api,core,services,schemas,db}/
    tests/
    Dockerfile
  infra/
    jenkins/Jenkinsfile
    sonarqube/  # optional configuration
  docs/
    architecture.md
    threat-model.md
    api-examples.md
    evidence/  # screenshots; avoid confidential information
  scripts/
    seed-demo-data.*
```

It is okay to adapt paths for language conventions, but keep names clear and backend modules cohesive. Avoid generated files in Git.

## 11. Environment and local execution

Provide an `.env.example` with placeholder values and clear comments, **never real secrets**:

```dotenv
POSTGRES_DB=structiq
POSTGRES_USER=structiq
POSTGRES_PASSWORD=change-me-local-only
JWT_SECRET=replace-with-long-random-secret
INTERNAL_AI_SHARED_SECRET=replace-with-a-different-random-secret
GEMINI_API_KEY=
GEMINI_GENERATION_MODEL=choose-a-supported-model
GEMINI_EMBEDDING_MODEL=gemini-embedding-001
EMBEDDING_DIMENSION=768
FRONTEND_ORIGIN=http://localhost:5173
```

- Use a `pgvector`-enabled PostgreSQL image and `CREATE EXTENSION IF NOT EXISTS vector` in migration/bootstrap.
- Add `Makefile` targets: `make up`, `make down`, `make logs`, `make test`, `make seed`, `make clean` (with a warning for destructive operations).
- Core Compose starts only necessary services; Jenkins and SonarQube only via opt-in profiles or external installations. Use named volumes and health checks.
- Service-to-service communication via internal Compose network. Expose the frontend and backend ports for local use; never directly publish AI internals. Database port is not published by default.
- README must include prerequisites, startup steps, how to generate secrets, test accounts creation, Gemini setup, and how to reset the demo.
- With no Gemini API key, projects, tasks, auth, dashboard, docs, and downloads all function; AI functionality is clearly disabled.

## 12. Tests: required security and functionality proof

### Backend integration / security tests
- Unauthenticated requests to protected endpoints return 401.
- User A belongs to Project A only, User B belongs to Project B only.
- User A cannot read Project B, list its tasks, access its documents, download a guessed document ID, change its tasks, or ask its AI endpoint for an answer.
- An authorized member cannot escalate self to manager or create/edit tasks beyond policy.
- A task cannot be assigned to a user outside its project.
- File ID/project ID mismatch is rejected.
- Invalid JWT / expired JWT / malformed payload are rejected.
- Input validation rejects dangerous/oversized file types, illegal states, and bad data.
- DAO/repository queries cannot leak data between projects.

### AI service tests (mock Gemini, do not require a paid API)
- Retrieval query **always** includes the trusted `project_id` restriction.
- Results contain only chunks from the authorized project and only READY docs.
- Tampered/expired internal authentication is rejected.
- A malicious instruction embedded inside a PDF is treated as data, not executed or trusted.
- Citation IDs and excerpts refer to retrieved allowed chunks.
- Empty retrieval produces a grounded "not enough information" response.
- Deleting a document invalidates/removes its vector chunks.

### Frontend / E2E tests
- Login -> create project -> add task -> update status -> dashboard metrics update.
- Manager uploads a text-based meeting PDF -> status READY -> asks question -> sees answer with valid citation when API key is configured (use deterministic mock provider for CI).
- No-Gemini-key mode displays helpful message without crashing.
- Two-user project isolation is demonstrated end-to-end.

Record actual test commands and outcomes in `docs/evidence/`; do not fabricate screenshots, scan results, coverage, test counts, or pass status.

## 13. CI/CD and security checks

Deliver:
- Jenkinsfile stages: checkout, backend build/test, frontend lint/test/build, AI lint/test, dependency/vulnerability scan, SonarQube analysis (configured as optional if not available), Docker builds, report/artifact archive.
- Secret injection via Jenkins credentials or environment; no inline tokens.
- OWASP Dependency-Check or a maintained dependency SCA alternative; Trivy image/filesystem scanning. If tool/network services are unavailable, report *skipped/blocked*, never falsely mark as passed.
- Backend secure headers / CORS allowlist, error sanitization, API-side RBAC; upload safety controls; container non-root where practical; secrets kept out of images.
- `docs/threat-model.md` describing assets, trust boundaries, threats (IDOR/BOLA, privilege escalation, prompt injection, cross-project retrieval, malicious upload, API key exposure), implemented controls and residual risks.
- An optional production deployment guide with HTTPS reverse proxy; localhost dev does not require live cloud hosting.

## 14. Implementation phases and acceptance gates

### Phase 0 — Foundation
**Implement:** Git-ready monorepo, `.gitignore`, `.env.example`, backend/frontend/AI skeleton, Dockerfiles, Compose with healthy PostgreSQL, README, Makefile, basic CI stubs, health endpoints, a frontend app shell.

**Pass when:** fresh clone can follow README, `make up` runs core services, backend/AI health endpoints and web page load, and no secret is tracked. Report real commands/results.

### Phase 1 — Authentication and project boundaries
**Implement:** Flyway schema, users, BCrypt passwords, login JWT, security configuration, project creation/list/detail, memberships, protected APIs, basic React auth and projects pages, two-user seed.

**Pass when:** authorized project operations work, other-project access is denied, automated tests prove isolation. This is the first meaningful demo.

### Phase 2 — Tasks and dashboard
**Implement:** task CRUD + assignment policies, task status transitions, filters and deadlines, real dashboard KPI aggregation, polished UI and tests.

**Pass when:** project manager assigns a task and member updates only their own task status; dashboard reflects DB changes.

### Phase 3 — Documents
**Implement:** PDF/TXT upload and private storage, metadata/status, authorized download/deletion, file validation, extraction and document ingestion pipeline scaffolding.

**Pass when:** uploading works, unauthorized downloads are blocked, deletion cleans metadata/files/chunks, error states are visible.

### Phase 4 — Secure RAG
**Implement:** Gemini integration (configurable provider, API key server-side), chunking, embeddings, pgvector, filtered retrieval, grounded answer and citations, private authenticated Spring-to-FastAPI calls, AI UI, tests with mocked Gemini.

**Pass when:** user A can cite only Project A documents; user A's attempt to ask about Project B returns no cross-project data, including when asking via prompt injection; citation references are correct. The product degrades gracefully without an API key.

### Phase 5 — DevSecOps, proof, polish
**Implement:** Jenkinsfile, SonarQube-compatible configuration, vulnerability/dependency checks, E2E tests, final UX polish, detailed README, architecture diagram, threat model, demo seed, screenshots checklist and a short demo script.

**Pass when:** repeatable local startup, tests and verified security checks are documented, CI stages runnable or blockers stated, and the app can be demonstrated in under 5 minutes.

**Working agreement for Codex:** At each phase, implement source code (not just plans), run available tests/builds, fix failures where feasible, summarize changed files, report unrun checks accurately, update README and `docs/progress.md` with checkboxes, then move to the next phase when asked. Do not leave TODO placeholders for core behavior while marking a phase complete.

## 15. Demo seed and five-minute demonstration

Create a safe seeded **fictional** construction company, e.g.:
- Users: `admin@demo.local` (global admin), `manager@demo.local` (manager on Project A), `engineer@demo.local` (member on Project A), `outsider@demo.local` (member on Project B). Passwords are generated or configured at seed time; **no fixed password hardcoded into Git**.
- Project A: `Renovation of Riverside Offices`; Project B: `Warehouse Electrical Upgrade`.
- Sample tasks: site inspection, safety review, supplier follow-up, updated floor plans.
- Two sample meeting reports with clearly labeled fictional content and pending actions.

Demo route:
1. Login as manager; show dashboard and Project A.
2. Assign task to engineer; change status; show updated metrics.
3. Upload meeting report PDF; wait for index status READY.
4. Ask: "Which actions were agreed in the last meeting?" Show citations and follow the authorized source document.
5. Log in as outsider; prove Project A is invisible and that guessing a Project A URL/download or AI request returns access denied.
6. Show repo architecture, Jenkins pipeline/test results, and threat model.

## 16. Definition of done

- [ ] All core workflows are end to end and not mocked in the running app.
- [ ] Clean clone setup is documented and works on Ubuntu.
- [ ] JWT authentication + membership/RBAC authorization works and is tested.
- [ ] Project CRUD + membership management works.
- [ ] Tasks + dashboard metrics work and persist.
- [ ] PDF/TXT upload/download/deletion works with authorization.
- [ ] RAG answers cite authorized documents; unauthorized content is never retrieved.
- [ ] API schema and error format are documented.
- [ ] Automated backend/frontend/AI tests exist; actual pass/fail results are recorded.
- [ ] CI configuration is usable with explicit documented prerequisites.
- [ ] No real secrets, sensitive files, or fabricated results are committed.
- [ ] README includes architecture, screenshots, startup, security controls, limitations, and a demo script.

## 17. Prioritization and explicit non-goals

**Must-have:** real CRUD, scoped RBAC, PDF/TXT upload, real RAG with citations, security isolation tests, Docker Compose, professional UI.

**Nice-to-have if time permits:** email notifications, calendar, activity feed, DOCX parsing, live collaboration, Kanban drag-and-drop, global search, deployment to a VPS.

**Out of scope initially:** billing, real customer integrations, real company data, production SSO, Kubernetes, complex event-driven infrastructure, autonomous AI agents with external tool privileges, full OCR, multi-region architecture.

## 18. Official technical references

- Spring Boot documentation: https://docs.spring.io/spring-boot/
- Spring Security documentation: https://docs.spring.io/spring-security/reference/
- OWASP API Security Top 10 (authorization, IDOR/BOLA): https://owasp.org/API-Security/
- Google Gemini embeddings: https://ai.google.dev/gemini-api/docs/embeddings
- Google Gemini models and availability: https://ai.google.dev/gemini-api/docs/models
- pgvector (SQL querying, exact versus approximate filtering): https://github.com/pgvector/pgvector
- Codex agent instructions (`AGENTS.md`): https://developers.openai.com/cookbook/examples/codex/iterating-development-workflows-with-codex

# StructIQ

A project workspace with a document-grounded assistant. **Current milestone: Phase 4, secure RAG.** Manage projects/tasks and private PDF/TXT documents, and ask project-grounded questions with authorized citations using local Ollama Qwen models or optional Gemini. Core flows work without a provider key.

## Run locally

Prerequisites: Docker Engine with a running daemon, Docker Compose 2.24.4 or later (including current v5), Make, Python 3. Ubuntu is the primary development platform. The default stack requires no host Java, Node, PostgreSQL, or Gemini key. Allow outbound registry access for the first build and keep localhost ports 5173 and 8080 free.

```bash
make secrets
make config
make up
make seed
make smoke
make smoke-phase1
make smoke-phase2
make smoke-phase3
make smoke-phase4
```

Open http://localhost:5173. API status: http://localhost:8080/api/system/status. Backend health: http://localhost:8080/actuator/health; readiness includes PostgreSQL. The AI health route is internal:

```bash
docker compose exec -T ai python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8000/health').read().decode())"
```

`make secrets` creates `.env` with independently generated random values and permissions 0600, replacing only the documented placeholders. Existing configured values are preserved. Never commit `.env` or put secrets in frontend build variables. The backend requires a JWT secret of at least 32 bytes and uses it for HS256 access tokens. The independently generated internal service secret authenticates Spring-to-FastAPI extraction requests. It is never sent to the browser.

```bash
make test       # Build three test stages; verify Compose boundaries
make logs       # Follow core service logs
make down       # Stop services, preserve database
make clean CONFIRM=DELETE  # Destructive: delete the demo database volumes
```

After a reset, run `make up` again. PostgreSQL initialization enables pgvector on a fresh volume. `make seed` creates four synthetic accounts, two fictional projects, and five sample tasks. A random password is saved to `.demo-credentials.json` (mode 0600, ignored by Git), never printed or embedded in source. Read this file locally to sign in. Running seed again preserves the password and updates only these demo accounts, adding missing initial project fixtures. Existing projects and tasks are preserved on reseed; task due dates are relative to their first seed date.

### Explore the delivery showcase

With the seeded accounts and local Ollama configured, run `make showcase` to add **Riverside Delivery Lab**. It contains three fictional indexed coordination/design/risk documents and eight persisted tasks spanning all four statuses. The command checks a real answer with citations and preserves existing records on reruns. Dates are relative to the project's first creation; all data is synthetic. This is opt-in and calls the selected real AI provider.

Refresh http://localhost:5173 and sign in as `manager@demo.local`. Overview shows live delivery progress, unfinished work, and deadlines. Open **Riverside Delivery Lab** and use its **Tasks**, **Documents**, **AI assistant**, and **Team** navigation. The task board groups the current filtered page (up to 12 tasks); the List switch uses the same authorized records, and status changes persist through the existing API. Pagination is explicit.

In **AI assistant**, try: “What is blocking the fire-door installation, who owns the supplier follow-up, and what must happen before installation starts?” Or choose a briefing/next-step/deadline prompt, then send. The side library shows the first 12 authorized documents and their indexing status. Citations download through the authenticated endpoint. Answers format basic emphasis and lists as escaped React text, with no model-supplied HTML or links. Each question is independent; Ctrl/Cmd+Enter sends, and Clear conversation removes only the in-memory display. The empty project state links to document upload. Direct project-section links return to their destination after sign-in.

The redesign includes responsive layouts and reduced-motion styles. Browser visual and click verification remains unavailable in this environment; component rendering, TypeScript, production build, HTTP smoke checks and actual local RAG inference are recorded in `docs/progress.md`.

## Components and versions

| Component | Pinned version |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.2, Maven image 3.9.12 |
| API documentation | springdoc-openapi 3.2.0 |
| Frontend | React 19.3.1, TypeScript 7.0.2, Vite 8.3.6 |
| Navigation / queries | React Router 7.18.5, TanStack Query 5.106.1 |
| Styles / tests | Tailwind CSS 4.3.5, Vitest 5.1.0 |
| Internal parsing service | Python 3.12, FastAPI 0.162.0, Uvicorn 0.49.0, pypdf 7.0.0 |
| AI tests / HTTP / DB | pytest 9.0.3, HTTPX 0.28.1, psycopg[binary] 3.3.8 |
| Database | pgvector 0.8.2 on PostgreSQL 17 |
| Web server | unprivileged nginx 1.28.2 Alpine |

Direct dependencies are pinned in manifests; frontend transitive dependencies are recorded in `frontend/package-lock.json`. Container tags and Python transitive dependencies are not immutable locks. Official release references: [Spring Boot](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/), [Vite](https://www.npmjs.com/package/vite?activeTab=versions), [FastAPI](https://pypi.org/project/fastapi/0.162.0/).

The browser calls nginx, which proxies `/api/` to Spring Boot. PostgreSQL and FastAPI have no host-published ports and connect only to the internal Compose network. Backend and web ports bind to loopback. The backend and frontend share the edge network; the backend also joins the internal network. The private document named volume is mounted read/write by Spring and read-only by FastAPI; nginx has no access to it. AI additionally joins a provider-egress network for Gemini HTTPS; PostgreSQL remains on the isolated internal network and neither AI nor PostgreSQL publishes host ports. Runtime containers use unprivileged application users. Builds run tests before packaging.

`backend/` contains the Java API and security configuration; `frontend/` the React shell and typed status client; `ai-service/` the internal health service; `infra/` PostgreSQL initialization and a foundation Jenkins pipeline; `scripts/` setup, network-boundary checks, and smoke verification. See [progress](docs/progress.md) and [Phase 4 verification evidence](docs/evidence/phase-4.md) for observed results and limitations.

## Development and troubleshooting

Container builds are the supported checks. For frontend development use Node 22.12+ or 24 (the image uses Node 24): `cd frontend && npm ci && npm run dev`. Vite proxies requests to localhost:8080. Java development requires JDK 21 and Maven; run `mvn verify` from `backend/`. AI development requires Python 3.12 or newer: create a virtual environment, install `pip install -e '.[test]'` inside `ai-service/`, then run `pytest -q`.

If Docker reports permission denied, grant your local account Docker access according to your system's Docker installation guidance, then start a fresh login session. Registry or package download failures require working network access. Use `docker compose ps` and `docker compose logs --tail=100` to investigate startup failures. Readiness waits for the database; liveness alone does not prove database connectivity.

The backend uses BCrypt (cost 12), 15-minute HS256 JWTs with issuer/audience/expiry checks, stateless bearer authentication, and explicit project membership authorization. The frontend stores tokens only in memory; refreshing or signing out requires another login. JWT role claims do not grant project or admin rights: current database roles control access, including immediate membership revocation. Bearer-only API calls omit cookies; CSRF is disabled because no cookie or session authentication is configured.

Project list SQL filters membership before pagination. Nonmembers receive a generic 404; members/viewers receive 403 for manager operations. Creating a project requires ADMIN or an existing MANAGER membership; creators automatically become project managers. Member role changes and project mutations lock the project row; the last manager cannot be removed or demoted. A manager can add registered users by exact email. Task assignment is restricted to current members of that project by both service checks and a composite foreign key. Removing a member unassigns their tasks within the same transaction. All project members can download documents. ADMIN/MANAGER can delete any document; MEMBER can upload and delete their own uploads; VIEWER can read only.

Login is limited to ten attempts per remote address per minute, including successful attempts. This bounded, in-process limiter is for the single-instance local app; it resets on restart. Forwarded headers are ignored, so nginx-proxied local users share a limit. Wait until the next minute if repeated smoke checks receive 429. Production needs a trusted proxy policy and shared limiting across replicas.

Flyway creates users, projects, memberships, tasks, document metadata/pages, and a file-deletion outbox; parameterized JDBC repositories perform explicit scoped queries. The test image runs real HTTP integration tests with the same migration on H2 PostgreSQL mode; `make smoke-phase1`, `make smoke-phase2`, and `make smoke-phase3` additionally verify the flows against real PostgreSQL through nginx. Container builds do not require access to the host Docker socket for tests. Testcontainers and browser E2E coverage are not claimed.

Swagger UI is available at http://localhost:8080/swagger-ui/index.html and the generated schema at http://localhost:8080/v3/api-docs in local Compose. API docs are disabled by default outside Compose; do not enable `APP_DOCS_ENABLED` in a public production deployment. Swagger does not persist bearer authorization. [Spring JWT reference](https://www.springframework.org/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Flyway setup](https://docs.spring.io/spring-boot/how-to/data-initialization.html), and [springdoc compatibility](https://springdoc.org/faq.html) describe the integrations used here.

### Try the Phase 4 demo

1. Sign in as `manager@demo.local` using the generated password. Overview shows real accessible-project counts, open/overdue work, progress, and upcoming tasks.
2. Open Riverside, create a task and assign it to `engineer@demo.local`. Sign out and sign in as engineer: My Tasks allows a status update, while manager controls are absent. Complete the task and revisit Overview to see the changed metrics. A MEMBER can update only their own assigned task status; VIEWER has read access. Managers/admins can create, edit, reassign and delete tasks.
3. Sign in as `outsider@demo.local`: Riverside is absent. Opening a copied Riverside URL returns a generic resource-not-found error. The outsider manages Warehouse only.
4. Generate a fictional two-page meeting PDF with `python3 scripts/demo_documents.py`. Upload `/tmp/structiq-fictional-meeting.pdf` in a project’s Documents panel. Verify two extracted pages, download the file, and try the same document URL as outsider: access is denied. With no Gemini configuration, text remains available for later indexing. Once configured, new uploads index automatically; older uploads have an Index for AI action. Ask a project question, inspect its citations and download a source.
5. Sign in as `admin@demo.local` to bootstrap memberships or access all projects. These are local demo accounts; never seed them in production.

The phase smoke commands create and delete temporary verification projects; they preserve seeded projects and tasks. Project PATCH omitted/null fields retain old values (existing project dates cannot currently be cleared). Project deletion removes its document metadata/extracted pages, tasks, and memberships, and queues private file cleanup. See [API examples](docs/api-examples.md) and [threat model](docs/threat-model.md) for the current contract and limitations.

No Gemini key is needed for core flows or automated proof. Without both key and generation model, AI status is unconfigured and questions return 503 AI_NOT_CONFIGURED. Jenkins currently runs the existing build/test targets; vulnerability scans and SonarQube integration belong to Phase 5.


### Task and dashboard behavior

Task lists support status, priority, assignee, overdue flag, inclusive due-before date, pagination, and sorting by due date, priority, or last update. Assignee choices come only from the project's member endpoint. My Tasks uses the authenticated user on the server and includes only their assigned tasks in projects they can still access. Task status can move among TODO, IN_PROGRESS, BLOCKED, and DONE; there are no extra workflow gates in this phase. A VIEWER may be assigned work but cannot change status without a role change.

Task PATCH tracks field presence: omitted fields stay unchanged; explicit `assigneeId: null` unassigns and `dueDate: null` clears the deadline. Members may submit only a status field for their own task, including no extra null fields. Managers/admins can edit all task fields. Titles are required (up to 200 characters); descriptions allow 4000 characters. Task IDs and project IDs are checked together.

Dashboard reads a consistent database snapshot filtered to accessible projects. `assignedTasks` counts all tasks assigned to the caller; `openTasks` and `overdueTasks` count all accessible unfinished tasks. Overdue means due before today UTC; today is not overdue. Upcoming includes unfinished tasks due today through seven days ahead, inclusive. Counts include archived/completed projects when membership still grants access. Charts use real grouped counts. Progress is floor(completed / total × 100), or zero for no tasks. The overview shows up to eight projects by name and up to ten tasks per deadline list; full project/task screens provide pagination. Dashboard refreshes every 30 seconds and task mutations invalidate cached metrics immediately.


### Private document behavior

Open a project and use its Documents panel. Upload one PDF or plain UTF-8 TXT at a time. Default limit is 10 MiB; `UPLOAD_MAX_BYTES` can lower it (1–10485760 bytes). Spring validates extension, declared type, bounded size and content (PDF header/trailer; strict UTF-8 without binary/control/executable/markup signatures). The private parser additionally validates PDF structure. Corrupt PDFs may be stored with a visible FAILED extraction status. Files are served only as authorized attachments with `nosniff` and `no-store`; there is no public static upload URL. Original filenames are sanitized display values; storage uses independently generated project/document UUID paths. SHA-256 checksums are stored privately. Metadata responses contain no file paths, raw text or hashes.

Text extraction is synchronous: upload commits PROCESSING before calling the parser, then returns stored metadata after completion. FastAPI runs the parser in a fixed subprocess with a 20-second wall limit, 15-second CPU limit, 512 MiB address-space cap, at most 200 PDF pages and 500,000 extracted characters. TXT is section/page 1; PDF page numbers are retained for non-empty pages. Password-protected PDFs fail clearly. Scanned/blank PDFs fail with NO_TEXT; OCR is outside this phase. Parser errors are sanitized. If parsing is unavailable, the document remains downloadable with a FAILED status and Retry extraction action. Interrupted PROCESSING rows older than two minutes are marked failed by the periodic worker; retry processes the stored file.

Successful extraction first remains UPLOADED with `TEXT_EXTRACTED_AI_PENDING`. Private `document_pages` rows feed indexing. If AI is configured, upload attempts synchronous indexing; successful chunk/vector storage atomically changes the document to READY/INDEXED. Failed indexing leaves the original file and extracted pages usable, with an explicit Index for AI action for retry. With no key, no embeddings or generation calls occur. Document text is treated only as untrusted data, including any embedded instructions. [pypdf release](https://pypi.org/project/pypdf/7.0.0/) and [extraction reference](https://pypdf.readthedocs.io/en/latest/user/extract-text.html) document the parser used.

Spring signs each internal extraction request with HMAC-SHA256 over method, path, timestamp, random nonce, and body hash. FastAPI verifies signature, a 30-second time window, and nonce replay; it derives file paths only from signed project/document UUIDs. The service has no browser-published port or public docs. This nonce cache and parser capacity (two workers) are single-instance controls; production replicas need shared replay state and an appropriate workload policy.

Deleting metadata and extracted pages queues file deletion in the same database transaction. Cleanup runs immediately after commit and retries up to 100 queued files every 60 seconds; missing files count as already removed. Downloads are denied immediately after metadata deletion, including during a storage outage. Project deletion uses the same cleanup flow. Backups must include both PostgreSQL and the private volume. `make down` preserves both; `make clean CONFIRM=DELETE` destroys both. A hard crash between writing an upload and committing metadata can leave an inaccessible orphan file; automatic orphan reconciliation and malware scanning are not implemented. Do not treat file-type checks or parser resource limits as antivirus proof.

For native development, configure `DOCUMENT_ROOT` for both services to the same private directory, set `INTERNAL_AI_URL`, and supply the same independently generated `INTERNAL_AI_SHARED_SECRET`. Ubuntu/POSIX file permissions are required: Spring writes as UID/GID 10001; the parser receives read-only group 10001 access in Compose. Container startup provisions the volume permissions. A storage or parser configuration failure returns sanitized errors rather than fabricating extraction success.


### Enable local Ollama RAG (Qwen)

The owner’s local setup uses `qwen3:4b` for answers and `qwen3-embedding:0.6b` for embeddings. No cloud key is needed. Install/run Ollama on the Docker host with both models, then set these values in the ignored `.env`:

```dotenv
AI_PROVIDER=ollama
OLLAMA_BASE_URL=http://host.docker.internal:11434
OLLAMA_GENERATION_MODEL=qwen3:4b
OLLAMA_EMBEDDING_MODEL=qwen3-embedding:0.6b
EMBEDDING_DIMENSION=768
```

Run `make up`, refresh http://localhost:5173, and upload a PDF/TXT or use **Index for AI** on previously extracted documents. Once READY, ask about the document. Existing vectors from Gemini are excluded from Ollama retrieval; reindex existing documents after any provider/model change. Ollama configuration status indicates a selected adapter; unavailable servers/models surface a sanitized provider error when indexing/asking rather than a false successful answer.

On Linux, Compose maps `host.docker.internal` to the host gateway. Ollama must listen on an address reachable from that gateway; a host-loopback-only listener cannot be reached from the container. Check `ss -ltnp` and the [Ollama networking guide](https://docs.ollama.com/faq). Restrict Ollama access to the local host/container network: its API is not the app’s authorization boundary and must not be exposed to untrusted clients. The current owner’s server was already reachable; no host listener/firewall changes were made.

The adapter uses native [`/api/embed`](https://docs.ollama.com/api/embed) with requested 768 dimensions (validated and normalized), and [`/api/chat`](https://docs.ollama.com/api/chat) with a JSON answer/source schema, `think: false`, bounded context/output, and no tools. Qwen retrieval instructions prefix questions only. Vectors are tagged `ollama:qwen3-embedding:0.6b`, preventing accidental mixing with Gemini vectors. Provider requests have 60-second timeouts, the signed internal client 150 seconds and nginx 180 seconds; indexing retains its 45-second scheduling deadline plus a final in-flight request. First inference can be slower while the models load. The existing cosine-distance cutoff 0.35 is unchanged and requires broader quality evaluation per embedding model.

```bash
make smoke-ollama  # Real installed models, disposable synthetic project, no cloud calls
python3 scripts/ollama-smoke.py --keep-demo  # Leave a synthetic project to explore after success
```

The live check proves automatic READY indexing, a grounded answer with source download, outsider/revoked-member denials and viewer indexing denial. It requires an already running stack, seeded accounts, and the selected local Ollama adapter. Existing user projects/documents are preserved.

### Enable optional Gemini RAG

Set `AI_PROVIDER=gemini` before following these steps.

1. Obtain a Gemini API key in Google AI Studio and set it only in the ignored `.env` as `GEMINI_API_KEY`. Set `GEMINI_GENERATION_MODEL` to a model enabled for your account; `.env.example` uses [stable `gemini-3.6-flash`](https://ai.google.dev/gemini-api/docs/models/gemini-3.6-flash?hl=en). Keep `GEMINI_EMBEDDING_MODEL=gemini-embedding-001` and `EMBEDDING_DIMENSION=768` unless intentionally migrating/reindexing embeddings. No real Gemini request was made during the recorded verification; adapter requests were tested with mocked HTTP responses.
2. Run `make up` to recreate services with the new environment. The AI panel checks server configuration; keys never enter browser code or responses. API/model/429/timeout errors are sanitized and leave core flows usable.
3. Upload a text-based PDF/TXT or click Index for AI on an extracted upload. New configured uploads attempt indexing automatically. READY means chunks and embeddings committed successfully, not merely that parsing finished.
4. Ask a project-specific question. Answers include authorized filename/page/excerpt citations; clicking a source uses the authenticated download endpoint. Questions are independent; the last ten answers live only in browser memory and disappear on navigation/sign-out. Verify decisions against source documents.

Embedding requests use RETRIEVAL_DOCUMENT/RETRIEVAL_QUERY, explicitly request 768 output dimensions, validate finite nonzero vectors and normalize them. Chunks are at most 1200 characters with 200-character overlap within each original page; limits remain 500,000 extracted characters, 200 pages and 800 chunks. Embeddings are sent in batches of at most 32 with an indexing deadline. Model IDs and dimensions are stored with chunks and matched in retrieval, so changing models never silently mixes vectors. Reindex existing documents after a model change. See the [Gemini embedding reference](https://ai.google.dev/gemini-api/docs/embeddings) and [pgvector exact search](https://github.com/pgvector/pgvector).

Spring authenticates and authorizes the project before each AI call. It holds the project lock during indexing/questions to keep normal membership changes and document deletion stable while context is used. FastAPI verifies HMAC operation/body/project scope and queries only the signed project. Exact SQL applies project, document READY state, embedding model and 768 dimensions before ORDER BY/LIMIT 6. No ANN/global top-k then filtering is used. Context exceeding cosine distance 0.35 is rejected; empty/irrelevant retrieval yields a fixed insufficient-information answer without generation. This conservative threshold is not tuned on real customer data or a live Gemini benchmark.

Generation receives only the question and allowed excerpts as untrusted data, with no tools. Structured output selects integer source numbers; the service constructs citations from those retrieved rows. Spring checks citation chunk/document/project, READY state, filename, page and actual excerpt again, rejecting fabricated/stale/cross-project references. Chunk deletion cascades from document/project deletion. Short answers, bounded context/output, two service slots, 18-second provider request timeouts and a 45-second indexing scheduling deadline constrain work; a final in-flight batch can extend indexing by one request timeout. Membership/task/document mutations in the same project may wait during an AI call. No application-level per-user AI quota or distributed replay cache is implemented yet.

### Deterministic RAG proof without a key

```bash
make proof-rag
```

This builds a separate `structiq-rag-proof` Compose stack with fresh volumes, exposes its frontend on localhost:5174, and runs a test-image-only provider fixture. The normal runtime image contains no mocked provider. The proof exercises real nginx → Spring → signed FastAPI → PostgreSQL pgvector flows: PDF upload/auto-index, WHERE-before-top-1 despite closer foreign chunks, READY/model/dimension/FK restrictions, citations and source download, two-user/prompt scope, insufficient context, viewer/revocation and chunk cleanup. Its finally block stops only this named test stack and removes only its disposable volumes. Keep port 5174 free. Compose's [override tag](https://docs.docker.com/reference/compose-file/merge/) requires version 2.24.4 or later.

The normal stack remains on localhost:5173 and uses the real Gemini adapter when configured. `make smoke-phase4` verifies the default no-key response; if AI is configured it checks status and deliberately skips paid questions. The deterministic proof uses HTTP and component server-rendering tests, not browser clicks. Local Ollama is additionally exercised with `make smoke-ollama`. Browser access remains unavailable in this environment; live Gemini generation quality, Gemini account access, vulnerability scans and Jenkins execution are not claimed as verified.

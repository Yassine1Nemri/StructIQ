# StructIQ

A project workspace with a planned document-grounded assistant. **Current milestone: Phase 2, tasks and dashboard.** Sign in, manage projects and memberships, assign tasks, track deadlines, and inspect real dashboard metrics. Uploads and RAG arrive in later phases.

## Run locally

Prerequisites: Docker Engine with a running daemon, Docker Compose v2 or later, Make, Python 3. Ubuntu is the primary development platform. The default stack requires no host Java, Node, PostgreSQL, or Gemini key. Allow outbound registry access for the first build and keep localhost ports 5173 and 8080 free.

```bash
make secrets
make config
make up
make seed
make smoke
make smoke-phase1
make smoke-phase2
```

Open http://localhost:5173. API status: http://localhost:8080/api/system/status. Backend health: http://localhost:8080/actuator/health; readiness includes PostgreSQL. The AI health route is internal:

```bash
docker compose exec -T ai python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8000/health').read().decode())"
```

`make secrets` creates `.env` with independently generated random values and permissions 0600, replacing only the documented placeholders. Existing configured values are preserved. Never commit `.env` or put secrets in frontend build variables. The backend requires a JWT secret of at least 32 bytes and uses it for HS256 access tokens. The internal AI service secret is reserved for Phase 4.

```bash
make test       # Build three test stages; verify Compose boundaries
make logs       # Follow core service logs
make down       # Stop services, preserve database
make clean CONFIRM=DELETE  # Destructive: delete the demo database volumes
```

After a reset, run `make up` again. PostgreSQL initialization enables pgvector on a fresh volume. `make seed` creates four synthetic accounts, two fictional projects, and five sample tasks. A random password is saved to `.demo-credentials.json` (mode 0600, ignored by Git), never printed or embedded in source. Read this file locally to sign in. Running seed again preserves the password and updates only these demo accounts, adding missing initial project fixtures. Existing projects and tasks are preserved on reseed; task due dates are relative to their first seed date.

## Components and versions

| Component | Pinned version |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1.2, Maven image 3.9.12 |
| API documentation | springdoc-openapi 3.2.0 |
| Frontend | React 19.3.1, TypeScript 7.0.2, Vite 8.3.6 |
| Navigation / queries | React Router 7.18.5, TanStack Query 5.106.1 |
| Styles / tests | Tailwind CSS 4.3.5, Vitest 5.1.0 |
| AI health service | Python 3.12, FastAPI 0.162.0, Uvicorn 0.49.0 |
| AI tests | pytest 9.0.3, HTTPX 0.28.1 |
| Database | pgvector 0.8.2 on PostgreSQL 17 |
| Web server | unprivileged nginx 1.28.2 Alpine |

Direct dependencies are pinned in manifests; frontend transitive dependencies are recorded in `frontend/package-lock.json`. Container tags and Python transitive dependencies are not immutable locks. Official release references: [Spring Boot](https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/), [Vite](https://www.npmjs.com/package/vite?activeTab=versions), [FastAPI](https://pypi.org/project/fastapi/0.162.0/).

The browser calls nginx, which proxies `/api/` to Spring Boot. PostgreSQL and FastAPI have no host-published ports and connect only to the internal Compose network. Backend and web ports bind to loopback. The backend and frontend share the edge network; the backend also joins the internal network. Runtime containers use unprivileged application users. Builds run tests before packaging.

`backend/` contains the Java API and security configuration; `frontend/` the React shell and typed status client; `ai-service/` the internal health service; `infra/` PostgreSQL initialization and a foundation Jenkins pipeline; `scripts/` setup, network-boundary checks, and smoke verification. See [progress](docs/progress.md) and [Phase 2 verification evidence](docs/evidence/phase-2.md) for observed results and limitations.

## Development and troubleshooting

Container builds are the supported checks. For frontend development use Node 22.12+ or 24 (the image uses Node 24): `cd frontend && npm ci && npm run dev`. Vite proxies requests to localhost:8080. Java development requires JDK 21 and Maven; run `mvn verify` from `backend/`. AI development requires Python 3.12 or newer: create a virtual environment, install `pip install -e '.[test]'` inside `ai-service/`, then run `pytest -q`.

If Docker reports permission denied, grant your local account Docker access according to your system's Docker installation guidance, then start a fresh login session. Registry or package download failures require working network access. Use `docker compose ps` and `docker compose logs --tail=100` to investigate startup failures. Readiness waits for the database; liveness alone does not prove database connectivity.

The backend uses BCrypt (cost 12), 15-minute HS256 JWTs with issuer/audience/expiry checks, stateless bearer authentication, and explicit project membership authorization. The frontend stores tokens only in memory; refreshing or signing out requires another login. JWT role claims do not grant project or admin rights: current database roles control access, including immediate membership revocation. Bearer-only API calls omit cookies; CSRF is disabled because no cookie or session authentication is configured.

Project list SQL filters membership before pagination. Nonmembers receive a generic 404; members/viewers receive 403 for manager operations. Creating a project requires ADMIN or an existing MANAGER membership; creators automatically become project managers. Member role changes and project mutations lock the project row; the last manager cannot be removed or demoted. A manager can add registered users by exact email. Task assignment is restricted to current members of that project by both service checks and a composite foreign key. Removing a member unassigns their tasks within the same transaction. Document permissions arrive in Phase 3.

Login is limited to ten attempts per remote address per minute, including successful attempts. This bounded, in-process limiter is for the single-instance local app; it resets on restart. Forwarded headers are ignored, so nginx-proxied local users share a limit. Wait until the next minute if repeated smoke checks receive 429. Production needs a trusted proxy policy and shared limiting across replicas.

Flyway creates users, projects, and memberships; parameterized JDBC repositories perform explicit scoped queries. The test image runs real HTTP integration tests with the same migration on H2 PostgreSQL mode; `make smoke-phase1` and `make smoke-phase2` additionally verify the flows against real PostgreSQL through nginx. Container builds do not require access to the host Docker socket for tests. Testcontainers and browser E2E coverage are not claimed.

Swagger UI is available at http://localhost:8080/swagger-ui/index.html and the generated schema at http://localhost:8080/v3/api-docs in local Compose. API docs are disabled by default outside Compose; do not enable `APP_DOCS_ENABLED` in a public production deployment. Swagger does not persist bearer authorization. [Spring JWT reference](https://www.springframework.org/spring-security/reference/servlet/oauth2/resource-server/jwt.html), [Flyway setup](https://docs.spring.io/spring-boot/how-to/data-initialization.html), and [springdoc compatibility](https://springdoc.org/faq.html) describe the integrations used here.

### Try the Phase 2 demo

1. Sign in as `manager@demo.local` using the generated password. Overview shows real accessible-project counts, open/overdue work, progress, and upcoming tasks.
2. Open Riverside, create a task and assign it to `engineer@demo.local`. Sign out and sign in as engineer: My Tasks allows a status update, while manager controls are absent. Complete the task and revisit Overview to see the changed metrics. A MEMBER can update only their own assigned task status; VIEWER has read access. Managers/admins can create, edit, reassign and delete tasks.
3. Sign in as `outsider@demo.local`: Riverside is absent. Opening a copied Riverside URL returns a generic resource-not-found error. The outsider manages Warehouse only.
4. Sign in as `admin@demo.local` to bootstrap memberships or access all projects. These are local demo accounts; never seed them in production.

`make smoke-phase1` and `make smoke-phase2` create and delete temporary verification projects; they preserve seeded projects and tasks. Project PATCH omitted/null fields retain old values (existing project dates cannot currently be cleared). Project deletion removes its tasks and memberships. See [API examples](docs/api-examples.md) and [threat model](docs/threat-model.md) for the current contract and limitations.

No Gemini key is needed now. Keep `GEMINI_API_KEY` blank; generation model selection and provider integration will be implemented in Phase 4. Merely filling in a key does not enable AI in this release. Jenkins currently runs foundation tests only; vulnerability scans and SonarQube integration belong to Phase 5.


### Task and dashboard behavior

Task lists support status, priority, assignee, overdue flag, inclusive due-before date, pagination, and sorting by due date, priority, or last update. Assignee choices come only from the project's member endpoint. My Tasks uses the authenticated user on the server and includes only their assigned tasks in projects they can still access. Task status can move among TODO, IN_PROGRESS, BLOCKED, and DONE; there are no extra workflow gates in this phase. A VIEWER may be assigned work but cannot change status without a role change.

Task PATCH tracks field presence: omitted fields stay unchanged; explicit `assigneeId: null` unassigns and `dueDate: null` clears the deadline. Members may submit only a status field for their own task, including no extra null fields. Managers/admins can edit all task fields. Titles are required (up to 200 characters); descriptions allow 4000 characters. Task IDs and project IDs are checked together.

Dashboard reads a consistent database snapshot filtered to accessible projects. `assignedTasks` counts all tasks assigned to the caller; `openTasks` and `overdueTasks` count all accessible unfinished tasks. Overdue means due before today UTC; today is not overdue. Upcoming includes unfinished tasks due today through seven days ahead, inclusive. Counts include archived/completed projects when membership still grants access. Charts use real grouped counts. Progress is floor(completed / total × 100), or zero for no tasks. The overview shows up to eight projects by name and up to ten tasks per deadline list; full project/task screens provide pagination. Dashboard refreshes every 30 seconds and task mutations invalidate cached metrics immediately.

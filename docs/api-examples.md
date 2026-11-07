# Phase 4 API contract

All project routes and `/api/auth/me` require `Authorization: Bearer <accessToken>`. Tokens are obtained with `POST /api/auth/login` using a JSON body containing `email` and `password`. Login returns `{accessToken, tokenType: "Bearer", expiresIn: 900, user: {id, email, displayName, globalRole}}`; no password/hash is returned. Use the generated local credentials file; never paste a real token into source, logs, or shared screenshots.

Swagger UI and generated OpenAPI are available through the backend's localhost port in Compose. Documentation is disabled by default outside Compose. All examples below contain synthetic input; they assume a bearer-authenticated API client.

| Method and path | Input / result |
| --- | --- |
| `GET /api/auth/me` | Current database user DTO |
| `GET /api/projects?q=Riverside&status=ACTIVE&page=0&size=12` | `{items, page, size, hasNext}`; membership-filtered; size 1–100 |
| `GET /api/projects/permissions` | `{canCreate}` from current database roles |
| `POST /api/projects` | Create body below; 201 project DTO; creator is MANAGER |
| `GET /api/projects/{id}` | Project DTO with caller's current project role (or ADMIN) |
| `PATCH /api/projects/{id}` | Partial create fields; omitted/null values retain existing data; returns project DTO |
| `DELETE /api/projects/{id}` | Manager/admin; 204; cascade membership cleanup |
| `GET /api/projects/{id}/members` | Project member list `{userId, email, displayName, role}` |
| `POST /api/projects/{id}/members` | `{"email":"engineer@demo.local","role":"MEMBER"}`; manager/admin; 201 |
| `PATCH /api/projects/{id}/members/{userId}` | `{"role":"VIEWER"}`; manager/admin; 204 |
| `DELETE /api/projects/{id}/members/{userId}` | Manager/admin; 204 |

```json
{
  "name": "Fictional site inspection",
  "description": "Synthetic portfolio demonstration",
  "status": "PLANNED",
  "startDate": "2026-11-07",
  "targetDate": "2026-12-01"
}
```

Project statuses: PLANNED, ACTIVE, COMPLETED, ARCHIVED. Roles: MANAGER, MEMBER, VIEWER. Only ADMIN or a user with at least one manager membership can create projects. All members can view a project and its member list. Managers/admins can update/delete projects and manage memberships. Global USER is not a project role.

Names must contain non-whitespace text and fit 150 characters. Descriptions are limited to 4000 characters. Target dates cannot precede start dates. Search treats `%` and `_` literally. Membership email lookup is exact after lowercasing and trimming. Duplicate membership returns 409. Removing or demoting the last manager returns 409 `LAST_MANAGER`; add another manager first. Resource IDs are scoped to the project in member mutation queries.

Errors normally contain `{code, message, correlationId}`; `X-Correlation-ID` matches the request reference. Validation is 400, missing/invalid tokens 401, insufficient project role 403, missing resources/nonmembership 404, conflicts 409. Login attempts exceeding ten per remote address per minute return 429 `RATE_LIMITED` and `Retry-After: 60`. Infrastructure-level rejected CORS requests can use Spring's default CORS response. AI indexing and answers are available through the project-authorized endpoints below when Gemini is configured.


## Tasks

| Method and path | Result / permission |
| --- | --- |
| `GET /api/projects/{projectId}/tasks` | Any project member; paginated `{items,page,size,hasNext}` |
| `POST /api/projects/{projectId}/tasks` | Manager/admin; 201 task DTO |
| `PATCH /api/projects/{projectId}/tasks/{taskId}` | Manager/admin all fields; MEMBER only own assigned task's status |
| `DELETE /api/projects/{projectId}/tasks/{taskId}` | Manager/admin; 204 |
| `GET /api/tasks/mine` | Caller-assigned tasks only, filtered by current project access |
| `GET /api/dashboard/summary` | Current caller's accessible project/task counts and deadline lists |

```json
{
  "title": "Inspect foundations",
  "description": "Fictional construction task",
  "status": "TODO",
  "priority": "HIGH",
  "assigneeId": null,
  "dueDate": "2026-12-01"
}
```

Create requires title, status and priority. Title must contain non-whitespace text and fit 200 characters; description is at most 4000. Status: TODO, IN_PROGRESS, BLOCKED, DONE; priority: LOW, MEDIUM, HIGH. All valid statuses can transition to any other valid status. Assignee UUID must be a current member of the path project, including for admins. Invalid assignments return 400 `INVALID_ASSIGNEE` without exposing another user. VIEWER can read but cannot update tasks, including tasks assigned to them.

Task PATCH distinguishes omitted and explicit-null fields. `{"status":"DONE"}` is the only permitted shape for a MEMBER updating an assigned task. Extra fields, even null ones, are denied. Managers can use `{"assigneeId":null,"dueDate":null}` to clear those values. Null title/status/priority, unknown PATCH fields, and empty PATCH objects are invalid. Task DTO includes projectId/projectName, assigneeId/assigneeName, dueDate, createdBy, createdAt/updatedAt, and caller's current project role. It exposes no password hashes.

List query parameters: `status`, `priority`, `assigneeId`, `overdue=true|false`, `dueBefore=YYYY-MM-DD` (inclusive), `sort=DUE_DATE|PRIORITY|UPDATED`, `page=0`, `size=20` (1–100). Filters combine. Due-date sorting places unset dates last; priority sorting orders HIGH then MEDIUM then LOW, with due date and UUID tie-breakers. My Tasks applies the same filters but always restricts assignee to the authenticated caller. No query-provided user/project identifier can broaden that scope.

## Dashboard and project progress

Summary returns `asOf` (UTC date), `totalProjects`, `activeProjects`, `assignedTasks` (all caller-assigned statuses), `openTasks`, `overdueTasks`, `projectStatuses`, `taskStatuses`, `projectProgress`, `overdue`, and `upcoming`. All queries enforce current accessible projects; ADMIN can access all projects. Open/overdue include all unfinished tasks in accessible projects, including archived projects. Due today is upcoming, not overdue; upcoming extends through today + 7 days inclusive. Deadline lists contain up to 10 rows ordered earliest first. Project progress contains up to 8 projects ordered by name.

Project list/detail DTOs now also return `totalTasks`, `completedTasks`, and `progressPercent` derived from task queries. Empty projects show 0%; otherwise progress is integer floor(DONE / total × 100). Removing a member unassigns their tasks atomically, and project deletion removes tasks before memberships. The database additionally prevents assignments without matching project membership.


## Private documents

| Method and path | Result / permission |
| --- | --- |
| `GET /api/projects/{projectId}/documents?page=0&size=12` | Any project member; `{items,page,size,hasNext,maxUploadBytes}` |
| `POST /api/projects/{projectId}/documents` | Multipart field `file`; ADMIN/MANAGER/MEMBER; 201 metadata after synchronous extraction |
| `GET /api/projects/{projectId}/documents/{documentId}/download` | Any project member; attachment bytes; checks both path IDs |
| `POST /api/projects/{projectId}/documents/{documentId}/retry` | ADMIN/MANAGER/MEMBER; failed extraction only; 200 metadata or 409 NOT_RETRYABLE |
| `DELETE /api/projects/{projectId}/documents/{documentId}` | ADMIN/MANAGER any; MEMBER only own upload; VIEWER denied; 204 |

Use an API client's multipart upload control with `file`, matching PDF/TXT filename extension and Content-Type (`application/pdf` or `text/plain`). Default maximum 10 MiB, with effective server value in the list response. Empty file: 400 EMPTY_FILE; invalid content/type: 400 INVALID_FILE_TYPE; missing multipart file: 400 INVALID_UPLOAD; oversized: 413 FILE_TOO_LARGE. nginx also returns a JSON 413 for requests exceeding its 11 MiB envelope cap. Filenames are sanitized display values, never storage paths. Download sends attachment disposition, content length, `nosniff`, and `no-store`; use a bearer-authenticated fetch rather than a public URL.

Metadata fields: `id`, `projectId`, `filename`, `mimeType`, `sizeBytes`, `processingStatus`, `processingCode`, `extractedPages`, `uploadedBy`, `createdAt`. Never includes storage keys, checksums or private extracted text. Nonmembers and ID mismatches receive 404 without metadata. Revoked members cannot download with their old token.

States: PROCESSING/EXTRACTING_TEXT while synchronous parsing runs; UPLOADED/TEXT_EXTRACTED_AI_PENDING after successful extraction; FAILED with a sanitized code (NO_TEXT, ENCRYPTED_PDF, INVALID_PDF, INVALID_TEXT, EXTRACTION_LIMIT, EXTRACTION_TIMEOUT, PARSER_UNAVAILABLE, PROCESSING_INTERRUPTED, or generic extraction failure). READY/INDEXED means successful vector indexing. Blank/scanned PDFs remain downloadable but fail extraction; encrypted PDFs require an unencrypted replacement. Failed extraction can be retried. Parsed pages are private ingestion data, not a text-preview endpoint. Chunks/citations follow the Phase 4 contract below.

Deletion removes metadata/pages and transactionally records file cleanup; immediate cleanup is attempted after commit, with durable retry every minute. During a storage outage 204 still means access/metadata were removed and cleanup is queued. Project deletion applies the same process. Storage read errors return 503 STORAGE_UNAVAILABLE; no local path is exposed.


## Secure RAG

| Method and path | Result / permission |
| --- | --- |
| `GET /api/projects/{projectId}/ai/status` | Any project member; `{configured: boolean}`; no key or secret fields |
| `POST /api/projects/{projectId}/ai/ask` | Any project member including VIEWER; `{question}` (nonblank, max 2000 characters) |
| `POST /api/projects/{projectId}/documents/{documentId}/index` | ADMIN/MANAGER/MEMBER; extracted pages required; `{indexedChunks}` |

```json
{
  "answer": "The engineer owns the safety review.",
  "citations": [{
    "chunkId": "00000000-0000-0000-0000-000000000001",
    "documentId": "00000000-0000-0000-0000-000000000002",
    "filename": "meeting.pdf",
    "pageNumber": 1,
    "excerpt": "Fictional meeting: engineer owns the safety review."
  }],
  "retrievalCount": 1
}
```

The example is synthetic schema illustration, not a recorded Gemini response. Questions are independent; no conversation transcript is sent or stored. The path project is authorized by Spring before signing the internal request. Prompt-provided project IDs cannot change retrieval scope. SQL filters both chunk/document project IDs, READY state, exact embedding model and dimension before exact cosine ordering/top-6. Chunks farther than cosine distance 0.35 are rejected. No relevant context produces the fixed insufficient-information answer and an empty citations array without generation.

Source integers from the model must identify retrieved rows. The service constructs filename/page/excerpt citations; Spring rechecks permitted READY chunk/document IDs and actual excerpt text before returning them. Uncited freeform answers, fabricated IDs/filenames/pages/excerpts, stale and cross-project citations fail closed with 503 AI_INVALID_RESPONSE. Citations download through the existing authenticated document endpoint; no public storage URL is returned. Excerpts are capped at 200 Unicode code points (at most 400 UTF-16 code units).

Default no-key/model mode returns 503 AI_NOT_CONFIGURED. Provider rate limiting returns 429 AI_RATE_LIMITED; provider timeout/outage and invalid responses use sanitized 503 codes. None expose headers, keys, full provider diagnostics, embeddings or unrelated documents. Core project/task/document operations continue. If upload extraction succeeds and AI is configured, indexing is attempted automatically; otherwise text remains UPLOADED for later indexing. Index requires matching document/project IDs and extracted text; invalid source returns 404 or 409 NO_TEXT. Failed indexing never marks a new document READY.

Common Flyway V4 stores private chunk text, page/index, model and dimension; PostgreSQL-specific V5 adds mandatory vector(768). H2 integration validates authorization/citations without pretending to run vectors; `make proof-rag` separately validates real PostgreSQL SQL/vector behavior and full HTTP flow with a test-only provider.

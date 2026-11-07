# Phase 2 API contract

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

Errors normally contain `{code, message, correlationId}`; `X-Correlation-ID` matches the request reference. Validation is 400, missing/invalid tokens 401, insufficient project role 403, missing resources/nonmembership 404, conflicts 409. Login attempts exceeding ten per remote address per minute return 429 `RATE_LIMITED` and `Retry-After: 60`. Infrastructure-level rejected CORS requests can use Spring's default CORS response. Document/AI workflows remain unavailable in this phase.


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

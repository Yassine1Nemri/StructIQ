# Current threat model — Phase 2

Assets: user password hashes, access tokens, project metadata, memberships, and task details/assignment/deadlines. Only synthetic data is seeded. Private document contents, embeddings, Gemini calls, and upload storage are future-phase assets; no controls for those flows are claimed yet.

Trust boundaries: browser → loopback nginx → Spring API → private PostgreSQL. FastAPI is private and currently provides only health. Spring, not UI controls or JWT role claims, determines project access. Runtime image users are unprivileged. The frontend receives public DTOs only.

| Threat | Implemented control and proof |
| --- | --- |
| Project ID guessing / cross-project reads | Scoped repository queries and service membership checks; HTTP tests deny other-project detail/member reads; live smoke proves two-user isolation |
| Project mutation / member ID mismatch | Manager checks, project-row lock, and membership writes filtered by both project and user IDs; automated mutation isolation tests |
| Self-promotion / false role claims | Role permissions come from the database, not caller input or JWT role; HTTP tests deny member escalation even with an ADMIN claim |
| Stale privileges | Current user and membership loaded on each operation; tests revoke membership and demote admin while retaining the same token |
| Invalid / expired JWT | HS256-only decoder, minimum signing-secret length, issuer, audience, expiry and UUID subject validation; negative token tests |
| Password guessing / account enumeration | BCrypt cost 12, generic credential errors, dummy hash for unknown emails, bounded per-address login limiter; unit and HTTP rate-limit tests |
| SQL injection / leaking password hashes | Parameterized queries, escaped literal search wildcards, explicit public DTOs; malformed-input and response tests |
| CSRF / token persistence | Stateless Authorization bearer API with no cookie/session authentication; browser omits cookies and retains token in memory; client tests cover bearer clearing |
| Accidental service / secret exposure | AI/DB have no host ports; web/API bind loopback; ignored private `.env` and generated credentials; Compose boundary checks |
| Operational detail leakage | Sanitized error bodies with server-generated correlation IDs; detailed actuator endpoints denied; health exposes status/group names only |

Residual risks: localhost HTTP is for development, not deployment. Production needs HTTPS, a dedicated migration/runtime database privilege policy, a trusted reverse proxy, distributed rate limiting, and secret rotation. nginx-proxied local logins share one remote address and therefore one rate limit. Signing-key rotation invalidates existing tokens, but logout otherwise clears only the browser token; a stolen token is valid until expiry unless the user is deleted or loses resource permission. XSS can use an in-memory token while the page is active; CSP, React escaping, and no raw-HTML rendering reduce exposure but are not complete XSS proof.

The built-in seed is opt-in and local-only; generated credentials must not be used in real deployments. Managers can grant other registered users project access intentionally. Project member emails are visible to other members of that project. Last-manager changes are serialized on the project row. Validation rejects malformed requests without echoing bodies. Browser E2E, vulnerability scanning, upload safety, and prompt-injection/RAG tests remain future work. H2 integration tests are supplemented by PostgreSQL live smoke rather than claimed as PostgreSQL/Testcontainers integration tests.


## Phase 2 controls and proof

Task reads and writes are scoped by both project ID and task ID; HTTP tests reject guessed cross-project IDs even when the caller belongs to both projects. ADMIN/manager can manage tasks; MEMBER may update only status of their own assigned task. Presence-aware PATCH rejects extra fields for members even when null. VIEWER cannot update assigned tasks. Tests cover these cases and invalid enums, dates, title limits, and unknown PATCH fields.

Assignee membership is checked under the project row lock and enforced again by a composite database foreign key from `(project_id, assignee_id)` to membership. All normal task and membership mutations serialize on that project lock. Removing membership first unassigns that project's tasks within the same transaction, then removes membership; the removed user immediately loses task/My Tasks access. Project deletion explicitly clears tasks before deleting project/memberships, avoiding constraint-order conflicts. Tests exercise foreign-assignee rejection, a direct database bypass attempt, revocation/unassignment, and deletion cleanup.

Dashboard and My Tasks enforce current membership in SQL before returning rows, counts or deadline lists. Dashboard uses a repeatable-read transaction so grouped metrics and lists share a snapshot. Tests place tasks in two projects and prove only accessible counts/rows appear, including progress changes after completion. Live PostgreSQL smoke repeats task policy and metric-delta checks through nginx. Frontend rendering tests check member/viewer/manager controls and data-driven metrics, but do not substitute for backend authorization or browser E2E testing.

Remaining Phase 2 limitations: all known statuses may transition freely; concurrent edits are serialized but use last-writer-wins rather than version conflicts. Dashboard previews are bounded to eight projects and ten tasks per deadline list. Large-scale query tuning has not been measured. Seed deadlines are relative to first creation and preserved on reseed. Browser access is unavailable; visual QA and click-based E2E are unrun. Document and RAG trust boundaries will be implemented in later phases.

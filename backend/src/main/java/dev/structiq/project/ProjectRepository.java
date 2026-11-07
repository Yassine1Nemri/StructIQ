package dev.structiq.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import dev.structiq.user.UserRepository.User;
import static dev.structiq.project.ProjectDtos.*;

@Repository
public class ProjectRepository {
    private final JdbcTemplate db;
    private static final String SELECT = """
        SELECT p.*, CASE WHEN ? THEN 'ADMIN' ELSE m.role END AS caller_role,
          (SELECT COUNT(*) FROM tasks t WHERE t.project_id=p.id) AS total_tasks,
          (SELECT COUNT(*) FROM tasks t WHERE t.project_id=p.id AND t.status='DONE') AS completed_tasks
        FROM projects p LEFT JOIN project_members m ON m.project_id=p.id AND m.user_id=?
        WHERE (? OR m.user_id IS NOT NULL)
        """;
    private static final RowMapper<Project> MAPPER = (r, n) -> new Project(r.getObject("id", UUID.class),
        r.getString("name"), r.getString("description"), Status.valueOf(r.getString("status")),
        r.getObject("start_date", java.time.LocalDate.class), r.getObject("target_date", java.time.LocalDate.class),
        r.getObject("created_by", UUID.class), r.getString("caller_role"),r.getLong("total_tasks"),r.getLong("completed_tasks"),
        r.getLong("total_tasks")==0?0:(int)(r.getLong("completed_tasks")*100/r.getLong("total_tasks")));
    public ProjectRepository(JdbcTemplate db) { this.db = db; }
    public List<Project> list(User caller, String query, Status status, int limit, int offset) {
        return db.query(SELECT + " AND LOWER(p.name) LIKE ? ESCAPE '!' AND (CAST(? AS VARCHAR) IS NULL OR p.status=?) ORDER BY p.created_at DESC, p.id LIMIT ? OFFSET ?",
            MAPPER, caller.admin(), caller.id(), caller.admin(), "%" + query.toLowerCase(java.util.Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%",
            status == null ? null : status.name(), status == null ? null : status.name(), limit, offset);
    }
    public Optional<Project> find(User caller, UUID id) {
        return db.query(SELECT + " AND p.id=?", MAPPER, caller.admin(), caller.id(), caller.admin(), id).stream().findFirst();
    }
    public boolean canCreate(User user) {
        return user.admin() || db.queryForObject("SELECT COUNT(*) FROM project_members WHERE user_id=? AND role='MANAGER'",
            Integer.class, user.id()) > 0;
    }
    public void insert(User user, UUID id, Create input) {
        db.update("INSERT INTO projects(id,name,description,status,start_date,target_date,created_by) VALUES (?,?,?,?,?,?,?)",
            id, input.name().strip(), input.description() == null ? "" : input.description(), input.status().name(),
            input.startDate(), input.targetDate(), user.id());
        addMember(id, user.id(), Role.MANAGER);
    }
    public void lock(UUID projectId) {
        db.queryForList("SELECT id FROM projects WHERE id=? FOR UPDATE", projectId);
    }
    public void update(Project value) {
        db.update("UPDATE projects SET name=?,description=?,status=?,start_date=?,target_date=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",
            value.name(), value.description(), value.status().name(), value.startDate(), value.targetDate(), value.id());
    }
    public void delete(UUID id) {
        db.update("INSERT INTO pending_file_deletions(storage_key) SELECT storage_key FROM documents WHERE project_id=?",id);
        db.update("DELETE FROM documents WHERE project_id=?",id);
        // Clear tasks first: both their project and assignee membership reference this project.
        db.update("DELETE FROM tasks WHERE project_id=?",id);
        db.update("DELETE FROM projects WHERE id=?", id);
    }
    public List<Member> members(UUID projectId) {
        return db.query("SELECT u.id,u.email,u.display_name,m.role FROM project_members m JOIN users u ON u.id=m.user_id WHERE m.project_id=? ORDER BY u.email",
            (r, n) -> new Member(r.getObject("id", UUID.class), r.getString("email"), r.getString("display_name"),
                Role.valueOf(r.getString("role"))), projectId);
    }
    public void addMember(UUID projectId, UUID userId, Role role) {
        db.update("INSERT INTO project_members(id,project_id,user_id,role) VALUES (?,?,?,?)", UUID.randomUUID(), projectId, userId, role.name());
    }
    public void role(UUID projectId, UUID userId, Role role) {
        db.update("UPDATE project_members SET role=? WHERE project_id=? AND user_id=?", role.name(), projectId, userId);
    }
    public void remove(UUID projectId, UUID userId) {
        db.update("UPDATE tasks SET assignee_id=NULL,updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND assignee_id=?",projectId,userId);
        db.update("DELETE FROM project_members WHERE project_id=? AND user_id=?", projectId, userId);
    }
}

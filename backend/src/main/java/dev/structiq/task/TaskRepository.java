package dev.structiq.task;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import dev.structiq.user.UserRepository.User;
import static dev.structiq.task.TaskDtos.*;

@Repository
public class TaskRepository {
    private final JdbcTemplate db;
    public TaskRepository(JdbcTemplate db) { this.db=db; }
    private static final String SELECT = """
        SELECT t.*, p.name AS project_name, u.display_name AS assignee_name,
               CASE WHEN ? THEN 'ADMIN' ELSE m.role END AS caller_role
        FROM tasks t JOIN projects p ON p.id=t.project_id
        LEFT JOIN users u ON u.id=t.assignee_id
        LEFT JOIN project_members m ON m.project_id=t.project_id AND m.user_id=?
        WHERE (? OR m.user_id IS NOT NULL)
        """;
    private static final RowMapper<Task> MAPPER = (r,n) -> new Task(r.getObject("id",UUID.class),
        r.getObject("project_id",UUID.class),r.getString("project_name"),r.getString("title"),r.getString("description"),
        Status.valueOf(r.getString("status")),Priority.valueOf(r.getString("priority")),r.getObject("assignee_id",UUID.class),
        r.getString("assignee_name"),r.getObject("due_date",LocalDate.class),r.getObject("created_by",UUID.class),
        r.getObject("created_at",OffsetDateTime.class),r.getObject("updated_at",OffsetDateTime.class),r.getString("caller_role"));
    public Optional<Task> find(User user,UUID project,UUID task) {
        return db.query(SELECT+" AND t.project_id=? AND t.id=?",MAPPER,user.admin(),user.id(),user.admin(),project,task).stream().findFirst();
    }
    public List<Task> list(User user,UUID project,boolean mine,Status status,Priority priority,UUID assignee,
                           Boolean overdue,LocalDate dueBefore,Sort sort,int limit,int offset) {
        var sql=new StringBuilder(SELECT);
        var args=new ArrayList<Object>(List.of(user.admin(),user.id(),user.admin()));
        if(project!=null) { sql.append(" AND t.project_id=?"); args.add(project); }
        if(mine) { sql.append(" AND t.assignee_id=?"); args.add(user.id()); }
        if(status!=null) { sql.append(" AND t.status=?"); args.add(status.name()); }
        if(priority!=null) { sql.append(" AND t.priority=?"); args.add(priority.name()); }
        if(assignee!=null) { sql.append(" AND t.assignee_id=?"); args.add(assignee); }
        if(overdue!=null) {
            sql.append(overdue ? " AND t.status<>'DONE' AND t.due_date<?" : " AND (t.status='DONE' OR t.due_date IS NULL OR t.due_date>=?)");
            args.add(LocalDate.now(ZoneOffset.UTC));
        }
        if(dueBefore!=null) { sql.append(" AND t.due_date<=?"); args.add(dueBefore); }
        sql.append(switch(sort) {
            case DUE_DATE -> " ORDER BY t.due_date ASC NULLS LAST, t.id";
            case PRIORITY -> " ORDER BY CASE t.priority WHEN 'HIGH' THEN 0 WHEN 'MEDIUM' THEN 1 ELSE 2 END, t.due_date ASC NULLS LAST, t.id";
            case UPDATED -> " ORDER BY t.updated_at DESC, t.id";
        });
        sql.append(" LIMIT ? OFFSET ?"); args.add(limit); args.add(offset);
        return db.query(sql.toString(),MAPPER,args.toArray());
    }
    public boolean member(UUID project,UUID user) {
        return db.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?",Integer.class,project,user)>0;
    }
    public void insert(UUID id,UUID project,User user,Create input) {
        db.update("INSERT INTO tasks(id,project_id,title,description,status,priority,assignee_id,due_date,created_by) VALUES (?,?,?,?,?,?,?,?,?)",
            id,project,input.title().strip(),input.description()==null?"":input.description(),input.status().name(),input.priority().name(),input.assigneeId(),input.dueDate(),user.id());
    }
    public void update(Task value) {
        db.update("UPDATE tasks SET title=?,description=?,status=?,priority=?,assignee_id=?,due_date=?,updated_at=CURRENT_TIMESTAMP WHERE project_id=? AND id=?",
            value.title(),value.description(),value.status().name(),value.priority().name(),value.assigneeId(),value.dueDate(),value.projectId(),value.id());
    }
    public void delete(UUID project,UUID task) { db.update("DELETE FROM tasks WHERE project_id=? AND id=?",project,task); }
}

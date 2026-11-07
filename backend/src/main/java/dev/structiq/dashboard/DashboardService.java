package dev.structiq.dashboard;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import dev.structiq.user.UserRepository.User;

@Service
public class DashboardService {
    public record Progress(UUID projectId,String name,long totalTasks,long completedTasks,int percent) {}
    public record Deadline(UUID taskId,UUID projectId,String projectName,String title,String assigneeName,LocalDate dueDate) {}
    public record Summary(LocalDate asOf,long totalProjects,long activeProjects,long assignedTasks,long openTasks,
        long overdueTasks,Map<String,Long> projectStatuses,Map<String,Long> taskStatuses,List<Progress> projectProgress,
        List<Deadline> overdue,List<Deadline> upcoming) {}
    private final JdbcTemplate db;
    private static final String ACCESS=" (? OR EXISTS (SELECT 1 FROM project_members m WHERE m.project_id=p.id AND m.user_id=?)) ";
    public DashboardService(JdbcTemplate db) { this.db=db; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Summary summary(User user) {
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        Map<String,Long> projects=new LinkedHashMap<>(),tasks=new LinkedHashMap<>();
        for(String status:List.of("PLANNED","ACTIVE","COMPLETED","ARCHIVED")) projects.put(status,0L);
        for(String status:List.of("TODO","IN_PROGRESS","BLOCKED","DONE")) tasks.put(status,0L);
        db.query("SELECT p.status,COUNT(*) AS amount FROM projects p WHERE "+ACCESS+" GROUP BY p.status",
            r -> { projects.put(r.getString("status"),r.getLong("amount")); },user.admin(),user.id());
        db.query("SELECT t.status,COUNT(*) AS amount FROM tasks t JOIN projects p ON p.id=t.project_id WHERE "+ACCESS+" GROUP BY t.status",
            r -> { tasks.put(r.getString("status"),r.getLong("amount")); },user.admin(),user.id());
        long assigned=db.queryForObject("SELECT COUNT(*) FROM tasks t JOIN projects p ON p.id=t.project_id WHERE "+ACCESS+" AND t.assignee_id=?",Long.class,user.admin(),user.id(),user.id());
        long overdue=db.queryForObject("SELECT COUNT(*) FROM tasks t JOIN projects p ON p.id=t.project_id WHERE "+ACCESS+" AND t.status<>'DONE' AND t.due_date<?",Long.class,user.admin(),user.id(),today);
        var progress=db.query("""
            SELECT p.id,p.name,COUNT(t.id) AS total,SUM(CASE WHEN t.status='DONE' THEN 1 ELSE 0 END) AS completed
            FROM projects p LEFT JOIN tasks t ON t.project_id=p.id WHERE
            """+ACCESS+" GROUP BY p.id,p.name ORDER BY p.name,p.id LIMIT 8",(r,n) -> {
                long total=r.getLong("total"),done=r.getLong("completed");
                return new Progress(r.getObject("id",UUID.class),r.getString("name"),total,done,total==0?0:(int)(done*100/total));
            },user.admin(),user.id());
        return new Summary(today,projects.values().stream().mapToLong(Long::longValue).sum(),projects.get("ACTIVE"),assigned,
            tasks.entrySet().stream().filter(e -> !e.getKey().equals("DONE")).mapToLong(Map.Entry::getValue).sum(),overdue,
            projects,tasks,progress,deadlines(user,today,true),deadlines(user,today,false));
    }
    private List<Deadline> deadlines(User user,LocalDate today,boolean overdue) {
        String sql="SELECT t.id,t.project_id,p.name,t.title,u.display_name,t.due_date FROM tasks t JOIN projects p ON p.id=t.project_id LEFT JOIN users u ON u.id=t.assignee_id WHERE "+ACCESS+
            " AND t.status<>'DONE' AND "+(overdue?"t.due_date<?":"t.due_date>=? AND t.due_date<=?")+" ORDER BY t.due_date,t.id LIMIT 10";
        Object[] args=overdue?new Object[]{user.admin(),user.id(),today}:new Object[]{user.admin(),user.id(),today,today.plusDays(7)};
        return db.query(sql,(r,n) -> new Deadline(r.getObject("id",UUID.class),r.getObject("project_id",UUID.class),
            r.getString("name"),r.getString("title"),r.getString("display_name"),r.getObject("due_date",LocalDate.class)),args);
    }
}

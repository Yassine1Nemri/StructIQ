package dev.structiq.user;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit one-shot local seed; never enabled by the normal Compose service. */
@Component
@ConditionalOnProperty(name="structiq.demo-seed-enabled", havingValue="true")
public class DemoSeed implements ApplicationRunner {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final TransactionTemplate tx;
    private final ConfigurableApplicationContext context;
    private final String password;
    public DemoSeed(JdbcTemplate db, PasswordEncoder passwords, TransactionTemplate tx,
                    ConfigurableApplicationContext context, @Value("${structiq.demo-password}") String password) {
        this.db=db; this.passwords=passwords; this.tx=tx; this.context=context; this.password=password;
    }
    private UUID id(String value) { return UUID.nameUUIDFromBytes(("structiq-demo:" + value).getBytes(StandardCharsets.UTF_8)); }
    @Override public void run(ApplicationArguments args) {
        if (password.length() < 16 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalStateException("Demo password must contain 16 to 72 UTF-8 bytes");
        tx.executeWithoutResult(status -> {
            for (String name : new String[]{"admin", "manager", "engineer", "outsider"}) {
                String email = name + "@demo.local";
                if (db.queryForObject("SELECT COUNT(*) FROM users WHERE id=?", Integer.class, id(email)) == 0) {
                    db.update("INSERT INTO users(id,email,password_hash,display_name,global_role) VALUES (?,?,?,?,?)",
                        id(email), email, passwords.encode(password), "Demo " + name,
                        name.equals("admin") ? "ADMIN" : "USER");
                } else {
                    db.update("UPDATE users SET password_hash=? WHERE id=? AND email=?", passwords.encode(password), id(email), email);
                }
            }
            project("riverside", "Renovation of Riverside Offices", "manager");
            project("warehouse", "Warehouse Electrical Upgrade", "outsider");
            member("riverside", "engineer", "MEMBER");
            task("inspection","riverside","Site inspection","engineer","IN_PROGRESS","HIGH",-2);
            task("safety","riverside","Safety review","manager","TODO","HIGH",2);
            task("supplier","riverside","Supplier follow-up","engineer","BLOCKED","MEDIUM",5);
            task("plans","riverside","Updated floor plans","manager","DONE","LOW",-1);
            task("electrical","warehouse","Electrical survey","outsider","TODO","MEDIUM",3);
        });
        System.exit(SpringApplication.exit(context, () -> 0));
    }
    private void project(String key, String name, String manager) {
        if (db.queryForObject("SELECT COUNT(*) FROM projects WHERE id=?", Integer.class, id(key)) == 0) {
            db.update("INSERT INTO projects(id,name,description,status,created_by) VALUES (?,?,?,?,?)", id(key), name,
                "Fictional demonstration project. No real customer data.", "ACTIVE", id("admin@demo.local"));
            member(key, manager, "MANAGER");
        }
    }
    private void member(String project, String user, String role) {
        if (db.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?", Integer.class,
            id(project), id(user + "@demo.local")) == 0) {
            db.update("INSERT INTO project_members(id,project_id,user_id,role) VALUES (?,?,?,?)",
                id(project + ":" + user), id(project), id(user + "@demo.local"), role);
        }
    }
    private void task(String key,String project,String title,String assignee,String status,String priority,int dueOffset) {
        if(db.queryForObject("SELECT COUNT(*) FROM tasks WHERE id=?",Integer.class,id("task:"+key))==0) {
            UUID assigned=db.queryForObject("SELECT COUNT(*) FROM project_members WHERE project_id=? AND user_id=?",Integer.class,
                id(project),id(assignee+"@demo.local"))>0?id(assignee+"@demo.local"):null;
            db.update("INSERT INTO tasks(id,project_id,title,description,status,priority,assignee_id,due_date,created_by) VALUES (?,?,?,?,?,?,?,?,?)",
                id("task:"+key),id(project),title,"Fictional demonstration task.",status,priority,assigned,
                java.time.LocalDate.now(java.time.ZoneOffset.UTC).plusDays(dueOffset),id("admin@demo.local"));
        }
    }
}

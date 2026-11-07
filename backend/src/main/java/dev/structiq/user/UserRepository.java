package dev.structiq.user;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {
    public record User(UUID id, String email, String displayName, String globalRole, String passwordHash) {
        public UserView view() { return new UserView(id, email, displayName, globalRole); }
        public boolean admin() { return globalRole.equals("ADMIN"); }
    }
    public record UserView(UUID id, String email, String displayName, String globalRole) {}
    private final JdbcTemplate db;
    private static final RowMapper<User> MAPPER = (r, n) -> new User(r.getObject("id", UUID.class),
        r.getString("email"), r.getString("display_name"), r.getString("global_role"), r.getString("password_hash"));
    public UserRepository(JdbcTemplate db) { this.db = db; }
    public Optional<User> byEmail(String email) {
        return db.query("SELECT * FROM users WHERE email = ?", MAPPER, email).stream().findFirst();
    }
    public Optional<User> byId(UUID id) {
        return db.query("SELECT * FROM users WHERE id = ?", MAPPER, id).stream().findFirst();
    }
}

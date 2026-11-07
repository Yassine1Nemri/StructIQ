package dev.structiq.project;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.structiq.common.ApiException;
import dev.structiq.user.UserRepository;
import dev.structiq.user.UserRepository.User;
import static dev.structiq.project.ProjectDtos.*;

@Service
@Transactional
public class ProjectService {
    private final ProjectRepository projects;
    private final UserRepository users;
    private final dev.structiq.document.DocumentCleanup documentCleanup;
    public ProjectService(ProjectRepository projects, UserRepository users, dev.structiq.document.DocumentCleanup documentCleanup) {
        this.projects=projects; this.users=users; this.documentCleanup=documentCleanup;
    }
    public Page list(User user, String query, Status status, int page, int size) {
        if (page < 0 || page > 100_000 || size < 1 || size > 100 || query.length() > 150)
            throw new ApiException(400, "INVALID_INPUT", "Invalid pagination or search");
        var values = projects.list(user, query.strip(), status, size + 1, page * size);
        return new Page(values.stream().limit(size).toList(), page, size, values.size() > size);
    }
    public Project get(User user, UUID id) { return projects.find(user, id).orElseThrow(ApiException::missing); }
    public boolean canCreate(User user) { return projects.canCreate(user); }
    private Project manage(User user, UUID id) {
        projects.lock(id);
        var project = get(user, id);
        if (!project.role().equals("ADMIN") && !project.role().equals("MANAGER")) throw ApiException.forbidden();
        return project;
    }
    public Project create(User user, Create input) {
        if (!projects.canCreate(user)) throw ApiException.forbidden();
        dates(input.startDate(), input.targetDate());
        UUID id = UUID.randomUUID();
        projects.insert(user, id, input);
        return get(user, id);
    }
    public Project update(User user, UUID id, Update input) {
        var old = manage(user, id);
        var value = new Project(id, input.name() == null ? old.name() : input.name().strip(),
            input.description() == null ? old.description() : input.description(),
            input.status() == null ? old.status() : input.status(),
            input.startDate() == null ? old.startDate() : input.startDate(),
            input.targetDate() == null ? old.targetDate() : input.targetDate(), old.createdBy(), old.role(),old.totalTasks(),old.completedTasks(),old.progressPercent());
        dates(value.startDate(), value.targetDate());
        projects.update(value);
        return get(user, id);
    }
    private void dates(LocalDate start, LocalDate target) {
        if (start != null && target != null && target.isBefore(start))
            throw new ApiException(400, "INVALID_INPUT", "Target date must not precede start date");
    }
    public void delete(User user, UUID id) { manage(user, id); projects.delete(id); documentCleanup.afterCommit(); }
    public List<Member> members(User user, UUID id) { get(user, id); return projects.members(id); }
    public Member add(User user, UUID id, AddMember input) {
        manage(user, id);
        var added = users.byEmail(input.email().strip().toLowerCase(Locale.ROOT)).orElseThrow(ApiException::missing);
        projects.addMember(id, added.id(), input.role());
        return new Member(added.id(), added.email(), added.displayName(), input.role());
    }
    private void protectLastManager(UUID id, UUID memberId, Role replacement) {
        var members = projects.members(id);
        var target = members.stream().filter(m -> m.userId().equals(memberId)).findFirst().orElseThrow(ApiException::missing);
        if (target.role() == Role.MANAGER && replacement != Role.MANAGER
            && members.stream().filter(m -> m.role() == Role.MANAGER).count() == 1)
            throw new ApiException(409, "LAST_MANAGER", "Add another manager before removing or demoting the last manager");
    }
    public void role(User user, UUID id, UUID memberId, Role role) {
        manage(user, id); protectLastManager(id, memberId, role); projects.role(id, memberId, role);
    }
    public void remove(User user, UUID id, UUID memberId) {
        manage(user, id); protectLastManager(id, memberId, null); projects.remove(id, memberId);
    }
}

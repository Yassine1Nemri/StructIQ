package dev.structiq.project;

import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import dev.structiq.auth.AuthService;
import static dev.structiq.project.ProjectDtos.*;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {
    private final ProjectService projects;
    private final AuthService auth;
    public ProjectController(ProjectService projects, AuthService auth) { this.projects=projects; this.auth=auth; }
    public record Permissions(boolean canCreate) {}
    @GetMapping("/permissions") public Permissions permissions(@AuthenticationPrincipal Jwt jwt) {
        return new Permissions(projects.canCreate(auth.current(jwt)));
    }
    @GetMapping public Page list(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue="") String q,
        @RequestParam(required=false) Status status, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="20") int size) { return projects.list(auth.current(jwt), q, status, page, size); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Project create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Create input) {
        return projects.create(auth.current(jwt), input);
    }
    @GetMapping("/{id}") public Project get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return projects.get(auth.current(jwt), id);
    }
    @PatchMapping("/{id}") public Project update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
        @Valid @RequestBody Update input) { return projects.update(auth.current(jwt), id, input); }
    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) { projects.delete(auth.current(jwt), id); }
    @GetMapping("/{id}/members") public List<Member> members(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return projects.members(auth.current(jwt), id);
    }
    @PostMapping("/{id}/members") @ResponseStatus(HttpStatus.CREATED)
    public Member add(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @Valid @RequestBody AddMember input) {
        return projects.add(auth.current(jwt), id, input);
    }
    @PatchMapping("/{id}/members/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void role(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId,
        @Valid @RequestBody ChangeRole input) { projects.role(auth.current(jwt), id, userId, input.role()); }
    @DeleteMapping("/{id}/members/{userId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id, @PathVariable UUID userId) {
        projects.remove(auth.current(jwt), id, userId);
    }
}

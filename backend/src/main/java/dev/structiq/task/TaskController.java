package dev.structiq.task;

import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import dev.structiq.auth.AuthService;
import static dev.structiq.task.TaskDtos.*;

@RestController
@RequestMapping("/api")
public class TaskController {
    private final TaskService tasks;
    private final AuthService auth;
    public TaskController(TaskService tasks,AuthService auth) { this.tasks=tasks; this.auth=auth; }
    @GetMapping({"/projects/{projectId}/tasks","/tasks/mine"})
    public Page list(@AuthenticationPrincipal Jwt jwt,@PathVariable(required=false) UUID projectId,
        @RequestParam(required=false) Status status,@RequestParam(required=false) Priority priority,
        @RequestParam(required=false) UUID assigneeId,@RequestParam(required=false) Boolean overdue,
        @RequestParam(required=false) LocalDate dueBefore,@RequestParam(defaultValue="DUE_DATE") Sort sort,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return tasks.list(auth.current(jwt),projectId,projectId==null,status,priority,assigneeId,overdue,dueBefore,sort,page,size);
    }
    @PostMapping("/projects/{projectId}/tasks") @ResponseStatus(HttpStatus.CREATED)
    public Task create(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID projectId,@Valid @RequestBody Create input) {
        return tasks.create(auth.current(jwt),projectId,input);
    }
    @PatchMapping("/projects/{projectId}/tasks/{taskId}")
    public Task update(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID projectId,@PathVariable UUID taskId,
                       @Valid @RequestBody TaskPatch input) { return tasks.update(auth.current(jwt),projectId,taskId,input); }
    @DeleteMapping("/projects/{projectId}/tasks/{taskId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID projectId,@PathVariable UUID taskId) {
        tasks.delete(auth.current(jwt),projectId,taskId);
    }
}

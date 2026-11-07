package dev.structiq.ai;

import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import dev.structiq.auth.AuthService;

@RestController
@RequestMapping("/api/projects/{projectId}")
public class AiController {
    public record Question(@NotBlank @Size(max=2000) String question) {}
    private final AuthService auth;
    private final AiService ai;
    public AiController(AuthService auth,AiService ai) { this.auth=auth; this.ai=ai; }
    @GetMapping("/ai/status") public AiService.Status status(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID projectId) {
        return ai.status(auth.current(jwt),projectId);
    }
    @PostMapping("/ai/ask") public AiService.Answer ask(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID projectId,
        @Valid @RequestBody Question input) { return ai.ask(auth.current(jwt),projectId,input.question()); }
    @PostMapping("/documents/{documentId}/index") public AiService.IndexResult index(@AuthenticationPrincipal Jwt jwt,
        @PathVariable UUID projectId,@PathVariable UUID documentId) { return ai.index(auth.current(jwt),projectId,documentId); }
}

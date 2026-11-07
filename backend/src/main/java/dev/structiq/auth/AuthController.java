package dev.structiq.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import dev.structiq.user.UserRepository.UserView;
import dev.structiq.auth.AuthService.Login;
import dev.structiq.auth.AuthService.Session;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth=auth; }
    @PostMapping("/login")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public Session login(@Valid @RequestBody Login input, HttpServletRequest request) {
        return auth.login(input, request.getRemoteAddr());
    }
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) { return auth.current(jwt).view(); }
}

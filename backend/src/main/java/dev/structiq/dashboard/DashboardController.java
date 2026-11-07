package dev.structiq.dashboard;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import dev.structiq.auth.AuthService;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final AuthService auth;
    private final DashboardService dashboard;
    public DashboardController(AuthService auth,DashboardService dashboard) { this.auth=auth; this.dashboard=dashboard; }
    @GetMapping("/summary") public DashboardService.Summary summary(@AuthenticationPrincipal Jwt jwt) {
        return dashboard.summary(auth.current(jwt));
    }
}

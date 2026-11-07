package dev.structiq.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {
    public record SystemStatus(String name, String phase, String aiStatus) {}

    @GetMapping("/api/system/status")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    public SystemStatus status() {
        return new SystemStatus("StructIQ", "SECURE_RAG", "OPTIONAL_GEMINI");
    }
}

package dev.structiq.project;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class ProjectDtos {
    private ProjectDtos() {}
    public enum Status { PLANNED, ACTIVE, COMPLETED, ARCHIVED }
    public enum Role { MANAGER, MEMBER, VIEWER }
    public record Create(@NotBlank @Size(max=150) String name, @Size(max=4000) String description,
                         @NotNull Status status, LocalDate startDate, LocalDate targetDate) {}
    public record Update(@Pattern(regexp="(?s).*\\S.*") @Size(max=150) String name,
                         @Size(max=4000) String description, Status status,
                         LocalDate startDate, LocalDate targetDate) {}
    public record Project(UUID id, String name, String description, Status status, LocalDate startDate,
                          LocalDate targetDate, UUID createdBy, String role, long totalTasks, long completedTasks, int progressPercent) {}
    public record Page(List<Project> items, int page, int size, boolean hasNext) {}
    public record Member(UUID userId, String email, String displayName, Role role) {}
    public record AddMember(@NotBlank @Email @Size(max=254) String email, @NotNull Role role) {}
    public record ChangeRole(@NotNull Role role) {}
}

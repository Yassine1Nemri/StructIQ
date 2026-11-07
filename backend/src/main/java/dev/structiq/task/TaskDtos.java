package dev.structiq.task;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.*;

public final class TaskDtos {
    private TaskDtos() {}
    public enum Status { TODO, IN_PROGRESS, BLOCKED, DONE }
    public enum Priority { LOW, MEDIUM, HIGH }
    public enum Sort { DUE_DATE, PRIORITY, UPDATED }
    @io.swagger.v3.oas.annotations.media.Schema(name="TaskCreate")
    public record Create(@NotBlank @Size(max=200) String title, @Size(max=4000) String description,
                         @NotNull Status status, @NotNull Priority priority, UUID assigneeId, LocalDate dueDate) {}
    public record Task(UUID id, UUID projectId, String projectName, String title, String description,
                       Status status, Priority priority, UUID assigneeId, String assigneeName, LocalDate dueDate,
                       UUID createdBy, OffsetDateTime createdAt, OffsetDateTime updatedAt, String role) {}
    @io.swagger.v3.oas.annotations.media.Schema(name="TaskPage")
    public record Page(List<Task> items, int page, int size, boolean hasNext) {}
}

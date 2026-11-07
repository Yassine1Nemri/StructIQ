package dev.structiq.task;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import static dev.structiq.task.TaskDtos.*;

/** Presence tracking keeps omitted fields distinct from explicit null (unassign/clear due date). */
public class TaskPatch {
    private final Set<String> fields = new HashSet<>();
    private String title, description;
    private Status status;
    private Priority priority;
    private UUID assigneeId;
    private LocalDate dueDate;
    @Size(max=200) @Pattern(regexp="(?s).*\\S.*") public String getTitle() { return title; }
    @Size(max=4000) public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public Priority getPriority() { return priority; }
    public UUID getAssigneeId() { return assigneeId; }
    public LocalDate getDueDate() { return dueDate; }
    public void setTitle(String value) { fields.add("title"); title=value; }
    public void setDescription(String value) { fields.add("description"); description=value; }
    public void setStatus(Status value) { fields.add("status"); status=value; }
    public void setPriority(Priority value) { fields.add("priority"); priority=value; }
    public void setAssigneeId(UUID value) { fields.add("assigneeId"); assigneeId=value; }
    public void setDueDate(LocalDate value) { fields.add("dueDate"); dueDate=value; }
    @JsonIgnore public boolean has(String field) { return fields.contains(field); }
    @JsonIgnore public boolean statusOnly() { return fields.equals(Set.of("status")); }
    @JsonIgnore public boolean empty() { return fields.isEmpty(); }
    @JsonAnySetter public void unknown(String field, Object value) { throw new IllegalArgumentException("Unknown task field"); }
}

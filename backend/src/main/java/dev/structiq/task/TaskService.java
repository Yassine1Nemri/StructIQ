package dev.structiq.task;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import dev.structiq.common.ApiException;
import dev.structiq.project.ProjectRepository;
import dev.structiq.project.ProjectService;
import dev.structiq.user.UserRepository.User;
import static dev.structiq.task.TaskDtos.*;

@Service
@Transactional
public class TaskService {
    private final TaskRepository tasks;
    private final ProjectService projects;
    private final ProjectRepository projectRows;
    public TaskService(TaskRepository tasks,ProjectService projects,ProjectRepository projectRows) {
        this.tasks=tasks; this.projects=projects; this.projectRows=projectRows;
    }
    private boolean manager(String role) { return role.equals("MANAGER")||role.equals("ADMIN"); }
    private void assignee(UUID project,UUID user) {
        if(user!=null && !tasks.member(project,user)) throw new ApiException(400,"INVALID_ASSIGNEE","Assignee must be a current member of this project");
    }
    public Page list(User user,UUID project,boolean mine,Status status,Priority priority,UUID assignee,
                     Boolean overdue,LocalDate dueBefore,Sort sort,int page,int size) {
        if(project!=null) projects.get(user,project);
        if(page<0||page>100_000||size<1||size>100) throw new ApiException(400,"INVALID_INPUT","Invalid pagination");
        var values=tasks.list(user,project,mine,status,priority,assignee,overdue,dueBefore,sort,size+1,page*size);
        return new Page(values.stream().limit(size).toList(),page,size,values.size()>size);
    }
    public Task create(User user,UUID project,Create input) {
        projectRows.lock(project);
        if(!manager(projects.get(user,project).role())) throw ApiException.forbidden();
        assignee(project,input.assigneeId());
        UUID id=UUID.randomUUID(); tasks.insert(id,project,user,input);
        return tasks.find(user,project,id).orElseThrow(ApiException::missing);
    }
    public Task update(User user,UUID project,UUID id,TaskPatch input) {
        projectRows.lock(project);
        var access=projects.get(user,project);
        var old=tasks.find(user,project,id).orElseThrow(ApiException::missing);
        if(!manager(access.role()) && !(access.role().equals("MEMBER") && user.id().equals(old.assigneeId()) && input.statusOnly()))
            throw ApiException.forbidden();
        if(input.empty() || (input.has("title")&&input.getTitle()==null) || (input.has("status")&&input.getStatus()==null)
            || (input.has("priority")&&input.getPriority()==null)) throw new ApiException(400,"INVALID_INPUT","Supply valid task fields");
        UUID assigned=input.has("assigneeId")?input.getAssigneeId():old.assigneeId();
        assignee(project,assigned);
        var value=new Task(id,project,old.projectName(),input.has("title")?input.getTitle().strip():old.title(),
            input.has("description")?(input.getDescription()==null?"":input.getDescription()):old.description(),
            input.has("status")?input.getStatus():old.status(),input.has("priority")?input.getPriority():old.priority(),
            assigned,old.assigneeName(),input.has("dueDate")?input.getDueDate():old.dueDate(),old.createdBy(),old.createdAt(),old.updatedAt(),access.role());
        tasks.update(value);
        return tasks.find(user,project,id).orElseThrow(ApiException::missing);
    }
    public void delete(User user,UUID project,UUID id) {
        projectRows.lock(project);
        if(!manager(projects.get(user,project).role())) throw ApiException.forbidden();
        tasks.find(user,project,id).orElseThrow(ApiException::missing);
        tasks.delete(project,id);
    }
}

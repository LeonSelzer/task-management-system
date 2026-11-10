package taskmanagement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class TaskController {

    private final TaskService service;

    public TaskController (TaskService service) {
        this.service = service;
    }

    @GetMapping("/api/tasks")
    public ResponseEntity<List<TaskResponds>> getTasks(@RequestParam(required = false) String author, @RequestParam(required = false) String assignee) {
        if(author != null && assignee != null) {
            return ResponseEntity.ok(service.getAllTasksByOwnerAndAssignee(author,assignee));
        }
        if(author == null && assignee != null) {
            return ResponseEntity.ok(service.getAllTasksByAssignee(assignee));
        }
        if(author != null) {
            return ResponseEntity.ok(service.getAllTasksByOwner(author));
        }
        return ResponseEntity.ok(service.getAllTasks());
    }

    @PostMapping("/api/tasks")
    public ResponseEntity<TaskResponds> setTask(@Valid @RequestBody TaskInfo info, Authentication authentication) {
        return ResponseEntity.ok(service.createTask(info, authentication.getName()));
    }

    @PutMapping("/api/tasks/{id}/assign")
    public ResponseEntity<TaskResponds> assignTask(@PathVariable("id") Long id, @Valid @RequestBody AssigneeRequest assigneeRequest, Authentication authentication) {
        return ResponseEntity.ok(service.assignTask(id,assigneeRequest.assignee(), authentication.getName()));
    }

    @PutMapping("/api/tasks/{id}/status")
    public ResponseEntity<TaskResponds> setTaskStatus(@PathVariable("id") Long id, @RequestBody StateRequest stateRequest, Authentication authentication ) {
        return ResponseEntity.ok(service.setTaskStatus(id,stateRequest.status(), authentication.getName()));
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<Void> setTaskComment(@PathVariable("taskId") Long id,@Valid @RequestBody TaskCommentRequest taskCommentRequest, Authentication authentication) {
        service.setTaskComment(id, taskCommentRequest.text(), authentication.getName());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/tasks/{taskId}/comments")
    public ResponseEntity<List<TaskCommentResponds>> getTaskComments(@PathVariable("taskId") Long taskId) {
        return ResponseEntity.ok(service.getTaskComments(taskId));
    }





    public record TaskCommentRequest(@NotBlank String text){}

    public record StateRequest(@NotBlank String status){}

    public record TaskInfo(@NotBlank String title, @NotBlank String description) {}

    public record AssigneeRequest(@NotBlank String assignee){}
}

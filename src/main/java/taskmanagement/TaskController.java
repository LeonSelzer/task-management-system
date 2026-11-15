package taskmanagement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

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

    @PatchMapping("/api/tasks/{taskId}")
    public ResponseEntity<TaskResponds> patchTask(@PathVariable("taskId") Long taskId, @RequestBody TaskPatchRequest patchRequest, Authentication authentication) {
        Optional<String> potentialNewTitle = patchRequest.title();
        Optional<String> potentialNewDescription = patchRequest.description();

        if(potentialNewDescription.isEmpty() && potentialNewTitle.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Es muss mindestens ein Titel oder eine Dewscription angegeben werden");

        if(potentialNewDescription.isPresent() && potentialNewDescription.get().isBlank() || potentialNewTitle.isPresent() && potentialNewTitle.get().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Es dürfen keine leeren Angaben gemacht werden");

        if(potentialNewDescription.isPresent() && potentialNewTitle.isPresent()) {
            return ResponseEntity.ok(service.patchTaskTitleAndDescription(taskId,authentication.getName(),potentialNewTitle.get(),potentialNewDescription.get()));
        }

        return potentialNewDescription.map(s -> ResponseEntity.ok(service.patchTaskDescription(taskId, authentication.getName(), s))).orElseGet(() -> ResponseEntity.ok(service.patchTaskTitle(taskId, authentication.getName(), potentialNewTitle.get())));

    }




    public record TaskPatchRequest(Optional<String> title, Optional<String> description) {}

    public record TaskCommentRequest(@NotBlank String text){}

    public record StateRequest(@NotBlank String status){}

    public record TaskInfo(@NotBlank String title, @NotBlank String description) {}

    public record AssigneeRequest(@NotBlank String assignee){}
}

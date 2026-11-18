package taskmanagement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Controller
public class TaskController {

    private final TaskService service;

    public TaskController (TaskService service) {
        this.service = service;
    }

    @GetMapping("/api/tasks")
    public ResponseEntity<List<TaskResponds>> getTasks(@RequestParam(required = false) Optional<String> author, @RequestParam(required = false) Optional<String> assignee, @RequestParam(required = false)
    Optional<Priority> priority, @RequestParam(required = false) Optional<Boolean> overdue, @RequestParam(defaultValue = "20") @Max(100) @Min(1) Integer size, @RequestParam(defaultValue = "0") @Min(0) Integer page,
    @RequestParam(required = false) MultiValueMap<String,String> params, @RequestParam(required = false)@Valid Optional<State> status) {
        Page<TaskResponds> taskRespondsPage = service.getTasks(author,assignee,priority,overdue,size,page,params,status);
        return ResponseEntity.ok()
                .header("X-Total-Count",String.valueOf(taskRespondsPage.getTotalElements()))
                .header("X-Total-Pages",String.valueOf(taskRespondsPage.getTotalPages()))
                .body(taskRespondsPage.getContent());
    }

    @PostMapping("/api/tasks")
    public ResponseEntity<TaskResponds> setTask(@Valid @RequestBody TaskInfo info, Authentication authentication) {
        return ResponseEntity.ok(service.createTask(info.title(),info.description(), authentication.getName(), info.due_date(),info.priority()));
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
    public ResponseEntity<TaskResponds> patchTask(@PathVariable("taskId") Long taskId, @Valid @RequestBody TaskPatchRequest patchRequest, Authentication authentication) {
        return ResponseEntity.ok(service.patchTask(taskId,authentication.getName(),patchRequest.title(),patchRequest.description(),patchRequest.due_date(),patchRequest.priority()));
    }

    @DeleteMapping("/api/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable("taskId") Long taskId, Authentication authentication) {
        service.deleteTask(taskId, authentication.getName());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/api/tasks/{taskId}/comments/{commentId}")
    public ResponseEntity<TaskCommentResponds> changeComment(@PathVariable("taskId") Long taskId, @PathVariable("commentId") Long commentId, Authentication authentication, @Valid @RequestBody TaskCommentRequest taskCommentRequest) {
        return ResponseEntity.ok(service.changeComment(taskId,commentId,authentication.getName(),taskCommentRequest.text()));
    }


    @DeleteMapping("/api/tasks/{taskId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable("taskId") Long taskId, @PathVariable("commentId") Long commentId, Authentication authentication) {
        service.deleteComment(taskId,commentId,authentication.getName());
        return ResponseEntity.noContent().build();
    }

    public record TaskPatchRequest(Optional<String> title, Optional<String> description, Optional< @FutureOrPresent LocalDate> due_date, Optional<Priority> priority) {}

    public record TaskCommentRequest(@NotBlank String text){}

    public record StateRequest(@NotBlank String status){}

    public record TaskInfo(@NotBlank String title, @NotBlank String description, Optional<@FutureOrPresent LocalDate> due_date, Optional<Priority> priority) {}

    public record AssigneeRequest(@NotBlank String assignee){}
}

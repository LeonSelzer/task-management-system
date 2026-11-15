package taskmanagement;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class TaskService {

    static final String NO_ASSIGNEE = "none";

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskCommentRepository taskCommentRepository;

    public TaskService(TaskRepository repository, UserRepository userRepository, TaskCommentRepository taskCommentRepository) {
        this.taskRepository = repository;
        this.userRepository = userRepository;
        this.taskCommentRepository = taskCommentRepository;
    }

    public List<TaskResponds> getAllTasks() {
        return toResponses(taskRepository.findAll(Sort.by("id").descending()));
    }

    public List<TaskResponds> getAllTasksByOwner(String email) {
        String owner = normalizeEmail(email);
        return toResponses(taskRepository.findAllByOwner_Email(owner, Sort.by("id").descending()));
    }

    public List<TaskResponds> getAllTasksByAssignee(String email) {
        Sort sort = Sort.by("id").descending();
        if(NO_ASSIGNEE.equals(email)) {
            return toResponses(taskRepository.findAllByAssigneeIsNull(sort));
        }
        return toResponses(taskRepository.findAllByAssignee(normalizeEmail(email), sort));
    }

    public List<TaskResponds> getAllTasksByOwnerAndAssignee(String owner, String assignee ) {
        String normalizedOwner = normalizeEmail(owner);
        Sort sort = Sort.by("id").descending();
        if(NO_ASSIGNEE.equals(assignee)) {
            return toResponses(taskRepository.findAllByOwner_EmailAndAssigneeIsNull(normalizedOwner, sort));
        }
        return toResponses(taskRepository.findAllByOwner_EmailAndAssignee(normalizedOwner, normalizeEmail(assignee), sort));
    }



    public TaskResponds createTask(TaskController.TaskInfo info, String email) {
        AppUser owner = getOwner(email);
        AppTask task = taskRepository.saveAndFlush(new AppTask(info.title(),State.CREATED, info.description(),owner ));
        return new TaskResponds(task);
    }

    public TaskResponds assignTask(Long id, String assigneeEmail, String ownerEmail) {
        // "none" bedeutet: Zuweisung entfernen -> in der DB als null speichern
        String newAssignee = NO_ASSIGNEE.equals(assigneeEmail) ? null : normalizeEmail(assigneeEmail);
        if(newAssignee != null && !userRepository.existsByEmail(newAssignee)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Die User mit der email: " + assigneeEmail + " existiert nicht");
        AppTask task = getTaskById(id);

        if(!task.getOwner().getEmail().equals(normalizeEmail(ownerEmail))) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Nur der Owner einer Task darf sie einem Nutzer zuweisen");
        task.setAssignee(newAssignee);
        task = taskRepository.saveAndFlush(task);
        return new TaskResponds(task);
    }

    public TaskResponds setTaskStatus(Long id,String state, String stateChanger) {
        AppTask task = getTaskById(id);

        String owner = normalizeEmail(task.getOwner().getEmail());
        String possibleChanger = normalizeEmail(stateChanger);
        // Assignee kann null sein (nicht zugewiesen) -> null-sicher vergleichen
        boolean isAssignee = task.getAssignee() != null && normalizeEmail(task.getAssignee()).equals(possibleChanger);

        if(!(owner.equals(possibleChanger) || isAssignee)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Nur der Owner oder der Assignee können den Status ändern");
        State changedState = getState(state);
        task.setState(changedState);
        task = taskRepository.saveAndFlush(task);
        return new TaskResponds(task);
    }

    @Transactional
    public void setTaskComment(Long id, String text, String author) {
        AppTask task = getTaskById(id);
        author = normalizeEmail(author);
        TaskComment comment = new TaskComment(text,author,task);

        task.getComments().add(comment);

        taskRepository.saveAndFlush(task);
        taskCommentRepository.saveAndFlush(comment);
    }

    public List<TaskCommentResponds> getTaskComments(Long taskId) {
        if(!taskRepository.existsById(taskId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Die angegebene Task existiert nicht");
        List<TaskCommentResponds> comments = new ArrayList<>();
        for(TaskComment comment : taskCommentRepository.findAllByTaskId(taskId,Sort.by("id").descending())) {
            comments.add(new TaskCommentResponds(comment));
        }
        return comments;
    }

    public TaskResponds patchTaskTitleAndDescription(Long taskId, String author, String newTitle, String newDescription) {
        AppTask task = checkIfTaskExistsAndCorrectOwner(taskId,author);
        task.setDescription(newDescription);
        task.setTitle(newTitle);

        taskRepository.saveAndFlush(task);
        return new TaskResponds(task);
    }

    public TaskResponds patchTaskDescription(Long taskId, String author, String newDescription) {
        AppTask task = checkIfTaskExistsAndCorrectOwner(taskId,author);
        task.setDescription(newDescription);

        taskRepository.saveAndFlush(task);
        return new TaskResponds(task);
    }

    public TaskResponds patchTaskTitle(Long taskId, String author, String newTitle) {
        AppTask task = checkIfTaskExistsAndCorrectOwner(taskId,author);
        task.setTitle(newTitle);

        taskRepository.saveAndFlush(task);
        return new TaskResponds(task);
    }

    private AppTask checkIfTaskExistsAndCorrectOwner(Long taskId, String author) {
        if(!taskRepository.existsById(taskId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Die angegebene Task existiert nicht");

        AppTask task = getTaskById(taskId);

        String owner = normalizeEmail(task.getOwner().getEmail());
        String possibleChanger = normalizeEmail(author);

        if(!owner.equals(possibleChanger)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Nur der Owner kann den Title oder Description ändern");

        return task;
    }


    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private List<TaskResponds> toResponses(Iterable<AppTask> tasks) {
        List<AppTask> taskList = new ArrayList<>();

        tasks.forEach(taskList::add);

        List<Long> taskIds = taskList.stream()
                .map(AppTask::getId)
                .toList();

        Map<Long, Integer> countsByTaskId = new HashMap<>();
        for(TaskCommentRepository.CommentCount count : taskCommentRepository.countByTaskIds(taskIds)){
            countsByTaskId.put(count.getTaskId(), count.getTotalComments());
        }

        List<TaskResponds> responses = new ArrayList<>();
        for(AppTask task : taskList) {
            responses.add(new TaskResponds(task,countsByTaskId.getOrDefault(task.getId(),0)));
        }

        return responses;
    }

    private State getState(String newState) {
        return switch (newState) {
            case "CREATED" -> State.CREATED;
            case "IN_PROGRESS" -> State.IN_PROGRESS;
            case "COMPLETED" -> State.COMPLETED;
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Der angegebene Status existiert nicht");
        };
    }

    private AppTask getTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Die Task mit der " + id + " existiert nicht "
                ));
    }

    private AppUser getOwner(String email) {
        return  userRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(
                        () -> new UsernameNotFoundException("Der Benutzer existiert nicht"));
    }
}

package taskmanagement;

import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskService {

    static final String NO_ASSIGNEE = "none";

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskCommentRepository taskCommentRepository;

    private static final HashMap<String,String> whiteList;

    static {
        whiteList = new HashMap<>();
        whiteList.put("id","id");
        whiteList.put("title","title");
        whiteList.put("due_date","dueDate");
    }

    public TaskService(TaskRepository repository, UserRepository userRepository, TaskCommentRepository taskCommentRepository) {
        this.taskRepository = repository;
        this.userRepository = userRepository;
        this.taskCommentRepository = taskCommentRepository;
    }

    public Page<TaskResponds> getTasks(Optional<String> email, Optional<String> assignee, Optional<Priority> priority, Optional<Boolean> overdue, Integer size, Integer page, MultiValueMap<String,String> params, Optional<State> state) {
        Sort sort = generateSort(params).and(Sort.by("id").descending());
        Pageable pageable = PageRequest.of(page,size,sort);

        Specification<AppTask> spec = Specification.unrestricted();

        String mail = email.map(this::normalizeEmail).orElse(null);
        String assigneeMail = assignee.map(this::normalizeEmail).orElse(null);

        if(mail != null) spec = spec.and(TaskSpecifications.hasOwner(mail));
        if(assigneeMail != null) {
            if(NO_ASSIGNEE.equals(assigneeMail)) {
                spec = spec.and(TaskSpecifications.hasNoAssignee());
            }else {
                spec = spec.and(TaskSpecifications.hasAssignee(assigneeMail));
            }
        }
        if(priority.isPresent()) spec = spec.and(TaskSpecifications.hasPriority(priority.get()));
        if(overdue.isPresent()) {
            if(overdue.get()) spec = spec.and(TaskSpecifications.isOverdue());
            else spec = spec.and(Specification.not(TaskSpecifications.isOverdue()));
        }
        if(state.isPresent()) spec = spec.and(TaskSpecifications.hasState(state.get()));

        Page<AppTask> appTaskPage = taskRepository.findAll(spec,pageable);
        Map<Long,Integer> countsById = getCountsByTaskId(appTaskPage.getContent());

        return appTaskPage.map(task -> new TaskResponds(task,countsById.getOrDefault(task.getId(),0)));


    }

    public TaskResponds createTask(String title,String description, String email, Optional<LocalDate> dueDate, Optional<Priority> priority) {
        AppUser owner = getOwner(email);
        AppTask task = new AppTask(title,State.CREATED, description,owner);
        dueDate.ifPresent(task::setDueDate);
        priority.ifPresent(task::setPriority);

        taskRepository.saveAndFlush(task);

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

    public TaskResponds patchTask(Long taskId, String author, Optional<String> title, Optional<String> description, Optional<LocalDate> dueDate, Optional<Priority> priority) {
        if(title.isEmpty() && description.isEmpty() && dueDate.isEmpty() && priority.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Es muss mindestens ein Titel oder eine Dewscription angegeben werden");
        if(description.isPresent() && description.get().isBlank() || title.isPresent() && title.get().isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Es dürfen keine leeren Angaben gemacht werden");

        AppTask task = checkIfTaskExistsAndCorrectOwner(taskId,author);

        title.ifPresent(task::setTitle);
        description.ifPresent(task::setDescription);
        dueDate.ifPresent(task::setDueDate);
        priority.ifPresent(task::setPriority);

        taskRepository.saveAndFlush(task);
        return new TaskResponds(task);
    }

    public void deleteTask(Long taskId, String author) {
        taskRepository.delete(checkIfTaskExistsAndCorrectOwner(taskId,author));
    }

    public TaskCommentResponds changeComment(Long taskId, Long commentId, String author, String comment) {
        TaskComment taskComment = getCommentByIdAndTaskId(commentId,taskId);
        if(!taskComment.getAuthor().equals(author)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nur der Autor des Kommentars kann diesen ändern");

        taskComment.setText(comment);
        taskCommentRepository.saveAndFlush(taskComment);
        return new TaskCommentResponds(taskComment);
    }

    public void deleteComment(Long taskId, Long commentId, String author) {
        TaskComment taskComment = getCommentByIdAndTaskId(commentId,taskId);
        String taskOwner = taskComment.getTask().getOwner().getEmail();
        if(!taskComment.getAuthor().equals(author) && !taskOwner.equals(author)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Nur der Autor der Task oder des Kommentares dürfen diesen ändern");

        taskCommentRepository.delete(taskComment);
    }

    private AppTask checkIfTaskExistsAndCorrectOwner(Long taskId, String author) {
        AppTask task = getTaskById(taskId);

        String owner = normalizeEmail(task.getOwner().getEmail());
        String possibleChanger = normalizeEmail(author);

        if(!owner.equals(possibleChanger)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);

        return task;
    }


    private String normalizeEmail(String email) {
        return email.toLowerCase(Locale.ROOT);
    }

    private Map<Long,Integer> getCountsByTaskId(Collection<AppTask> tasks) {
        List<Long> taskIds = tasks.stream()
                .map(AppTask::getId)
                .toList();

        return  taskCommentRepository.countByTaskIds(taskIds).stream()
                .collect(Collectors.toMap((TaskCommentRepository.CommentCount::getTaskId), TaskCommentRepository.CommentCount::getTotalComments));
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

    private TaskComment getCommentByIdAndTaskId(Long commentId,Long taskId) {
        return taskCommentRepository.findByIdAndTaskId(commentId,taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Der Task mit der Id " + taskId  + " existiert nicht oder der Kommentar mit der Id " + commentId + " gehört nicht zu der Task" ));
    }

    private Sort generateSort(MultiValueMap<String,String> params) {
        List<String> sorts = params.getOrDefault("sort",List.of());
        List<Sort.Order> orders = sorts.stream()
                .map(this::parseOrder)
                .toList();
        return Sort.by(orders);
    }

    private Sort.Order parseOrder(String value) {
        String[] parts = value.split(",");
        if (parts.length > 2) throw badRequest("Ungültiges Sortierformat: " + value);

        String property = whiteList.get(parts[0].trim());
        if (property == null) throw badRequest("Unbekanntes Sortierfeld: " + parts[0]);

        Sort.Direction direction = parts.length == 2 ? parseDirection(parts[1]) : Sort.Direction.ASC;
        return new Sort.Order(direction, property);
    }

    private Sort.Direction parseDirection(String value) {
        try {
            return Sort.Direction.fromString(value);
        }catch (IllegalArgumentException ex) {
            throw badRequest("Ungültiges Sortierformat: " + value);
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }


}

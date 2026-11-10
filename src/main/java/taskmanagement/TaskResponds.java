package taskmanagement;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

public class TaskResponds {
    private String id;
    private String title;
    private String description;
    private State status;
    private String author;
    private String assignee;

    @JsonProperty("total_comments")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer total_comments;

    public TaskResponds(AppTask task, Integer totalComments) {
        this.id = String.valueOf(task.getId());
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.status = task.getState();
        this.author = task.getOwner().getEmail();
        this.assignee = task.getAssignee() == null ? "none" : task.getAssignee();
        this.total_comments = totalComments;
    }

    public TaskResponds(AppTask task) {
        this.id = String.valueOf(task.getId());
        this.title = task.getTitle();
        this.description = task.getDescription();
        this.status = task.getState();
        this.author = task.getOwner().getEmail();
        this.assignee = task.getAssignee() == null ? "none" : task.getAssignee();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public State getStatus() {
        return status;
    }

    public void setStatus(State status) {
        this.status = status;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Integer getTotal_comments() {
        return total_comments;
    }

    public void setTotal_comments(Integer total_comments) {
        this.total_comments = total_comments;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }
}

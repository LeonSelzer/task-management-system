package taskmanagement;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
public class AppTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    private String description;

    private State state;

    @ManyToOne
    @JoinColumn(name = "owner")
    private AppUser owner;

    private String assignee;

    @OneToMany(mappedBy = "task", cascade = CascadeType.REMOVE)
    private List<TaskComment> comments = new ArrayList<>();

    protected AppTask(){}

    public AppTask(String title, State state, String description, AppUser owner) {
        this.title = title;
        this.state = state;
        this.description = description;
        this.owner = owner;
        this.assignee = null;
    }

    public AppTask(String title, State state, String description, AppUser owner,String assignee) {
        this.title = title;
        this.state = state;
        this.description = description;
        this.owner = owner;
        this.assignee = assignee;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<TaskComment> getComments() {
        return comments;
    }

    public void setComments(List<TaskComment> comments) {
        this.comments = comments;
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

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public AppUser getOwner() {
        return owner;
    }

    public void setOwner(AppUser owner) {
        this.owner = owner;
    }

    public String getAssignee() {
        return assignee;
    }

    public void setAssignee(String assignee) {
        this.assignee = assignee;
    }
}

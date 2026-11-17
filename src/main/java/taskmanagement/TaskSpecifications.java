package taskmanagement;

import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class TaskSpecifications {

    private TaskSpecifications() {};

    public static Specification<AppTask> hasOwner(String email) {
        return  ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("owner").get("email"),email));
    }

    public static Specification<AppTask> hasAssignee(String email) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("assignee"),email));
    }

    public static Specification<AppTask> isOverdue() {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.lessThan(root.get("dueDate"), LocalDate.now()));
    }

    public static Specification<AppTask> hasPriority(Priority priority) {
        return ((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("priority"), priority));
    }

}

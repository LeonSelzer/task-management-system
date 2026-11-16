package taskmanagement;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


public interface TaskRepository extends JpaRepository<AppTask,Long> {

    public Iterable<AppTask> findAllByOwner_Email(String owner, Sort sort);

    public Iterable<AppTask> findAllByAssignee(String assignee, Sort sort);

    public Iterable<AppTask> findAllByOwner_EmailAndAssignee(String owner, String assignee, Sort sort);

    public Iterable<AppTask> findAllByAssigneeIsNull(Sort sort);

    public Iterable<AppTask> findAllByOwner_EmailAndAssigneeIsNull(String owner, Sort sort);


}

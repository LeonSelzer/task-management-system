package taskmanagement;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TaskCommentRepository extends JpaRepository<TaskComment,Long> {

    public Iterable<TaskComment> findAllByTaskId(Long task_id, Sort sort);


    interface CommentCount {
        Long getTaskId();
        Integer getTotalComments();
    }

    @Query("""
    select c.task.id as taskId, count(c) as totalComments
    from TaskComment c
    where c.task.id in :taskIds
    group by c.task.id
    """)
    List<CommentCount> countByTaskIds(@Param("taskIds") List<Long> taskIds);
}

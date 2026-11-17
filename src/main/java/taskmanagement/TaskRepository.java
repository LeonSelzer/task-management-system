package taskmanagement;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.function.BooleanSupplier;


public interface TaskRepository extends JpaRepository<AppTask,Long>, JpaSpecificationExecutor<AppTask> {

}

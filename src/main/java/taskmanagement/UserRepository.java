package taskmanagement;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface UserRepository extends CrudRepository<AppUser,String> {

    boolean existsByEmail(String email);

    Optional<AppUser> findByEmail(String email);
}

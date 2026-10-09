package ir.linuxian.second.repos;

import ir.linuxian.second.entities.User;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository

@RepositoryRestResource(exported = false)
public interface UserRepo extends CrudRepository<User, Long> {
    Optional<User> findByUsername(String username);
}

package ir.linuxian.second.repos;

import ir.linuxian.second.entities.Role;
import ir.linuxian.second.entities.RoleName;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepo extends CrudRepository<Role,Long> {

    Optional<Role> findByRoleName(RoleName roleName);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.users")
    List<Role> findAllWithUsers();
}


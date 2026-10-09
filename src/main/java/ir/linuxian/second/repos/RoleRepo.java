package ir.linuxian.second.repos;

import ir.linuxian.second.entities.Role;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface RoleRepo extends CrudRepository<Role,Long> {

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.users")
    List<Role> findAllWithUsers();
}


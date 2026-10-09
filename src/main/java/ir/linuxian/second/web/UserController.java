package ir.linuxian.second.web;

import ir.linuxian.second.entities.Role;
import ir.linuxian.second.entities.User;
import ir.linuxian.second.repos.RoleRepo;
import ir.linuxian.second.repos.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    @Autowired
    public UserController(UserRepo userRepo, RoleRepo roleRepo) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
    }

    @GetMapping("/users")
    public Iterable<User> getUsers() {
        return userRepo.findAll();
    }
    @GetMapping("/roles")
    public Iterable<Role> getroles() {
        return roleRepo.findAll();
    }



}

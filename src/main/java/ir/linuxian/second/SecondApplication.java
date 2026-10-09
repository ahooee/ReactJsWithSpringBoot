package ir.linuxian.second;

import ir.linuxian.second.entities.Role;
import ir.linuxian.second.entities.RoleName;
import ir.linuxian.second.entities.User;
import ir.linuxian.second.repos.RoleRepo;
import ir.linuxian.second.repos.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.ArrayList;
import java.util.List;

@SpringBootApplication
//@EnableJpaRepositories(basePackages = "ir.linuxian.second.repos")

public class SecondApplication implements CommandLineRunner {

	Logger logger = LoggerFactory.getLogger(SecondApplication.class);
	private final UserRepo userRepo;
	private final RoleRepo roleRepo;

	public SecondApplication(UserRepo userRepo,RoleRepo roleRepo) {

		this.userRepo = userRepo;
		this.roleRepo = roleRepo;
	}

	public static void main(String[] args) {
		SpringApplication.run(SecondApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {

Role admin = new Role(RoleName.ROLE_ADMIN);
Role user = new Role(RoleName.ROLE_USER);
Role moderator = new Role(RoleName.ROLE_MODERATOR);

List<Role> roles = new ArrayList<>();
roles.add(admin);
roles.add(moderator);

roleRepo.save(admin);
roleRepo.save(user);
roleRepo.save(moderator);

		userRepo.save(new User("mohammad", new BCryptPasswordEncoder().encode("linuxian"),"admin",List.of(user)));
		userRepo.save(new User("ahmad", new BCryptPasswordEncoder().encode("linuxian0"),"user",List.of(admin)));
		userRepo.save(new User("hamed", new BCryptPasswordEncoder().encode("linuxian1"),"admin",List.of(moderator)));
	userRepo.save(new User("hamid", new BCryptPasswordEncoder().encode("linuxian2"),"guest",roles));
	roles.add(user);
		userRepo.save(new User("fahimeh", "linuxian2","user",roles));
		userRepo.save(new User("masoomed","ldskjflkdsjf","user",List.of(admin)));




		for(User u : userRepo.findAll()) {

			logger.info("username: {}, password: {}, roles: {}",u.getUsername(), u.getPassword(),u.getRoles());
		}

		roleRepo.findAllWithUsers().forEach(role -> {

			          logger.info("roleName: {},users: {}",role.toString(),role.getUsers());
		});


	}
}

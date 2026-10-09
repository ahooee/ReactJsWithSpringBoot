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

		// Idempotent seeding: only create the defaults on an empty database so
		// restarting the app does not duplicate users (which breaks login).
		if (roleRepo.count() == 0) {
			roleRepo.save(new Role(RoleName.ROLE_ADMIN));
			roleRepo.save(new Role(RoleName.ROLE_USER));
			roleRepo.save(new Role(RoleName.ROLE_MODERATOR));
		}

		if (userRepo.count() == 0) {
			BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
			userRepo.save(new User("mohammad", encoder.encode("linuxian"), "admin", new ArrayList<>()));
			userRepo.save(new User("ahmad", encoder.encode("linuxian0"), "user", new ArrayList<>()));
			userRepo.save(new User("hamed", encoder.encode("linuxian1"), "admin", new ArrayList<>()));
			logger.info("Seeded default users. Admin login: mohammad / linuxian");
		}

		for (User u : userRepo.findAll()) {
			logger.info("username: {}, role: {}", u.getUsername(), u.getRole());
		}
	}
}

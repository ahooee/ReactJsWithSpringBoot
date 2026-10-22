package ir.linuxian.second.service;

import ir.linuxian.second.entities.Role;
import ir.linuxian.second.entities.User;
import ir.linuxian.second.repos.UserRepo;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Service
public class UserDetailServiceImpl implements UserDetailsService {

    private final UserRepo userRepo;

    public UserDetailServiceImpl(UserRepo userRepo) {
        this.userRepo = userRepo;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<User> user = userRepo.findByUsername(username);

        if (user.isEmpty()) {
            throw new UsernameNotFoundException("Username not found");
        }

        User currentUser = user.get();
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();

        // Legacy single-role string column ("admin", "user", ...)
        if (currentUser.getRole() != null && !currentUser.getRole().isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + currentUser.getRole().trim().toUpperCase()));
        }

        // Join-table roles (ROLE_ADMIN, ROLE_USER, ...)
        for (Role r : currentUser.getRoles()) {
            if (r.getRoleName() != null) {
                String name = r.getRoleName().name();
                if (!name.startsWith("ROLE_")) {
                    name = "ROLE_" + name;
                }
                authorities.add(new SimpleGrantedAuthority(name.toUpperCase()));
            }
        }

        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }

        return org.springframework.security.core.userdetails.User
                .withUsername(currentUser.getUsername())
                .password(currentUser.getPassword())
                .authorities(authorities)
                .build();
    }
}

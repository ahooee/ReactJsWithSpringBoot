package ir.linuxian.second.service;

import ir.linuxian.second.entities.User;
import ir.linuxian.second.repos.UserRepo;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import  org.springframework.security.core.userdetails.User.UserBuilder;
import java.util.Optional;

@Service
public class UserDetailServiceImpl implements UserDetailsService {

    private final UserRepo userRepo;

    public UserDetailServiceImpl(UserRepo userRepo) {
        this.userRepo = userRepo;
    }
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException{

        Optional<User> user = userRepo.findByUsername(username);

        UserBuilder builder = null;

        if(user.isPresent()){
            User currentUser = user.get();

            builder = org.springframework.security.core.userdetails.User.withUsername(currentUser.getUsername());
            builder.password(currentUser.getPassword());
            builder.roles(currentUser.getRole());


        }else {
            throw  new UsernameNotFoundException("Username not found");
        }


        return builder.build();
    }
}

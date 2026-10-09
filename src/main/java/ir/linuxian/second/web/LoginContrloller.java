package ir.linuxian.second.web;

import ir.linuxian.second.entities.AccountCredentials;
import ir.linuxian.second.service.JwtService;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;




@RestController
public class LoginContrloller {

    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public LoginContrloller(JwtService jwtService, AuthenticationManager authenticationManager) {
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<?> getToken(@RequestBody AccountCredentials accountCredentials) {
        UsernamePasswordAuthenticationToken creds = new UsernamePasswordAuthenticationToken(accountCredentials.username(), accountCredentials.password());
        Authentication authentication = authenticationManager.authenticate(creds);

        String jwts = jwtService.getToken(authentication.getName());

        return ResponseEntity.ok().header(HttpHeaders.AUTHORIZATION, "Bearer " + jwts).header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Authorization").build();

    }


}

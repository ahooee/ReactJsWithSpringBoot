package ir.linuxian.second.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

private final UserDetailsService userDetailsService;

public SecurityConfig(UserDetailsService userDetailsService) {
    this.userDetailsService = userDetailsService;
}

    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(
        new BCryptPasswordEncoder());
    }
    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {

    return   authenticationConfiguration.getAuthenticationManager();
    }


    @Bean
    public SecurityFilterChain secureFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())

                .cors(cors -> {})

                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        )
                )

                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests

                                .requestMatchers(HttpMethod.POST, "/login")
                                .permitAll()

                                .requestMatchers(HttpMethod.OPTIONS, "/**")
                                .permitAll()

                                .requestMatchers(HttpMethod.GET, "/api/pages/**")
                                .permitAll()

                                .requestMatchers(HttpMethod.GET, "/api/hello/**")
                                .permitAll()

                                .requestMatchers(HttpMethod.GET, "/api/menus/**")
                                .permitAll()

                                .requestMatchers(HttpMethod.POST, "/api/menus/**")
                                .permitAll()

                                .requestMatchers(HttpMethod.PUT, "/api/menus/**")
                                .permitAll()

                                .requestMatchers(HttpMethod.DELETE, "/api/menus/**")
                                .permitAll()
                                .requestMatchers("/error").permitAll()
                                .anyRequest()
                                .authenticated()
                );

        return http.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(
                List.of("http://localhost:5173")
        );

        configuration.setAllowedMethods(
                List.of(
                        "GET",
                        "POST",
                        "PUT",
                        "DELETE",
                        "OPTIONS"
                )
        );

        configuration.setAllowedHeaders(
                List.of("*")
        );

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration(
                "/**",
                configuration
        );

        return source;
    }
}

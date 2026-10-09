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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(UserDetailsService userDetailsService, JwtAuthFilter jwtAuthFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthFilter = jwtAuthFilter;
    }

    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(new BCryptPasswordEncoder());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain secureFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                // Auth
                                .requestMatchers(HttpMethod.POST, "/login").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/signup").permitAll()
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                                // Admin-only listings that hide drafts
                                .requestMatchers(HttpMethod.GET, "/api/posts/all").hasRole("admin")

                                // Public read access to site content
                                .requestMatchers(HttpMethod.GET, "/api/hello/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/pages/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/menus/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/media/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()

                                // Admin-only writes for CMS content
                                .requestMatchers(HttpMethod.POST, "/api/posts/**").hasRole("admin")
                                .requestMatchers(HttpMethod.PUT, "/api/posts/**").hasRole("admin")
                                .requestMatchers(HttpMethod.DELETE, "/api/posts/**").hasRole("admin")
                                .requestMatchers(HttpMethod.POST, "/api/products/**").hasRole("admin")
                                .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("admin")
                                .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("admin")
                                .requestMatchers(HttpMethod.POST, "/api/pages/**").hasRole("admin")
                                .requestMatchers(HttpMethod.PUT, "/api/pages/**").hasRole("admin")
                                .requestMatchers(HttpMethod.DELETE, "/api/pages/**").hasRole("admin")
                                .requestMatchers(HttpMethod.POST, "/api/media/**").hasRole("admin")
                                .requestMatchers(HttpMethod.DELETE, "/api/media/**").hasRole("admin")
                                .requestMatchers(HttpMethod.POST, "/api/menus/**").hasRole("admin")
                                .requestMatchers(HttpMethod.PUT, "/api/menus/**").hasRole("admin")
                                .requestMatchers(HttpMethod.DELETE, "/api/menus/**").hasRole("admin")

                                // Docs and static
                                .requestMatchers(HttpMethod.GET, "/").permitAll()
                                .requestMatchers(HttpMethod.GET, "/index.html").permitAll()
                                .requestMatchers(HttpMethod.GET, "/favicon.ico").permitAll()
                                .requestMatchers("/swagger-ui.html").permitAll()
                                .requestMatchers("/swagger-ui/**").permitAll()
                                .requestMatchers("/api-docs").permitAll()
                                .requestMatchers("/api-docs/**").permitAll()
                                .requestMatchers("/error").permitAll()

                                .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(
                List.of("http://localhost:*", "https://*.base44.dev", "https://*.base44.app")
        );

        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );

        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}

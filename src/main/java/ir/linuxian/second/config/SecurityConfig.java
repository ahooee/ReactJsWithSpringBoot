package ir.linuxian.second.config;

import ir.linuxian.second.components.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
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
                .cors(cors -> {
                })
                .sessionManagement(sessionManagement ->
                        sessionManagement.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorizeRequests ->
                        authorizeRequests
                                .requestMatchers(HttpMethod.POST, "/login").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/auth/password/change").authenticated()
                                .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/auth/register/countries").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                // Public reads (WordPress-like public site)
                                .requestMatchers(HttpMethod.GET, "/api/pages/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/posts/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/products/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/books/**").permitAll()
                                .requestMatchers(HttpMethod.HEAD, "/api/books/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/menus/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/settings").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/media/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/hello/**").permitAll()
                                // Guest basket (no login required)
                                .requestMatchers("/api/basket/**").permitAll()
                                // Guest checkout + guest order tracking + pay (secret accessToken)
                                .requestMatchers(HttpMethod.POST, "/api/orders/checkout").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/orders/track/*").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/orders/track/*/pay").permitAll()
                                // Zarinpal gateway callback + payment lookup (public)
                                .requestMatchers(HttpMethod.GET, "/api/payments/zarinpal/callback").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/payments/by-authority/*").permitAll()
                                // Bot Manager (admin dashboard)
                                .requestMatchers("/api/admin/bot/**").hasRole("ADMIN")
                                // Book file uploads to Telegram (admin dashboard)
                                .requestMatchers("/api/admin/books/**").hasRole("ADMIN")
                                // Messenger (admin dashboard, Telegram-web-like)
                                .requestMatchers("/api/admin/messenger/**").hasRole("ADMIN")
                                // Support tickets (admin dashboard)
                                .requestMatchers("/api/admin/tickets/**").hasRole("ADMIN")
                                // Support tickets (registered users)
                                .requestMatchers("/api/tickets/**").authenticated()
                                // Orders (registered users)
                                .requestMatchers("/api/orders/**").authenticated()
                                // Orders (admin dashboard)
                                .requestMatchers("/api/admin/orders/**").hasRole("ADMIN")
                                // Database backup & restore (admin dashboard, 2FA-gated)
                                .requestMatchers("/api/admin/backup/**").hasRole("ADMIN")
                                // Notifications (registered users)
                                .requestMatchers("/api/notifications/**").authenticated()
                                // Writes require admin (WordPress-like)
                                .requestMatchers(HttpMethod.POST, "/api/products/*/reviews").authenticated()
                                .requestMatchers(HttpMethod.POST, "/api/products/*/reactions").permitAll()
                                .requestMatchers(HttpMethod.POST, "/api/pages/**", "/api/posts/**", "/api/products/**", "/api/categories/**", "/api/menus/**", "/api/media/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.PUT, "/api/pages/**", "/api/posts/**", "/api/products/**", "/api/categories/**", "/api/menus/**", "/api/settings/**").hasRole("ADMIN")
                                .requestMatchers(HttpMethod.DELETE, "/api/pages/**", "/api/posts/**", "/api/products/**", "/api/categories/**", "/api/menus/**", "/api/media/**", "/api/reviews/**").hasRole("ADMIN")
                                .requestMatchers("/error").permitAll()
                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**").permitAll()
                                .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}

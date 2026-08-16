package com.dms.config;

import com.dms.security.JwtFilter;
import com.dms.service.AuditLogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    // Custom JWT filter to validate tokens in incoming requests
    private final JwtFilter jwtFilter;
    private final AuditLogService auditLogService;

    public SecurityConfig(JwtFilter jwtFilter, AuditLogService auditLogService) {
        this.jwtFilter = jwtFilter;
        this.auditLogService = auditLogService;
    }

    /**
     * Records requests refused by the filter chain.
     *
     * The controller advice only sees denials thrown inside a controller. A
     * request blocked before it gets that far - wrong role for the URL rule -
     * never reaches it, and those are exactly the ones worth knowing about.
     *
     * Only 403s are recorded, not 401s: an unauthenticated request is usually a
     * expired session or a page loading before login, and logging every one of
     * those would bury the real signal under noise.
     */
    @Bean
    public AccessDeniedHandler auditingAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            auditLogService.tryRecordCurrentUser(
                    "PERMISSION_DENIED",
                    null,
                    request.getRemoteAddr(),
                    "FAILED",
                    request.getMethod() + " " + request.getRequestURI());

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"You do not have permission to perform this action.\"}");
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:3000", "http://localhost:3001"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                // Enable CORS for frontend-backend communication
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Disable CSRF because we use stateless JWT authentication
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Define authorization rules for endpoints
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints
                        .requestMatchers("/auth/**").permitAll()

                        // The mock ERP stands in for an external system. It is not
                        // part of the DMS API and holds no DMS data - the connector
                        // reaches it over HTTP exactly as it would reach a real ERP.
                        .requestMatchers("/mock-erp/**").permitAll()

                        // Signing identifies the signer from the JWT, so it can never be public.
                        .requestMatchers("/api/signatures/**").authenticated()

                        // Share links. The GET routes are addressed by an
                        // unguessable token rather than by identity - that is
                        // what a share link is - so they stay public. Every
                        // route that writes (create, revoke, save an annotated
                        // version) needs a real account.
                        .requestMatchers(HttpMethod.POST, "/api/share-links").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/share-links/*/access").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/share-links/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/share-links/**").permitAll()

                        // Comments: a share recipient may read the thread with
                        // only the token, but writing one is attributed to a
                        // person, so it requires a login. Leaving the write
                        // methods open made an unauthenticated PUT surface as a
                        // 500 from the service instead of a clean 401.
                        .requestMatchers(HttpMethod.GET, "/api/comments/**").permitAll()
                        .requestMatchers("/api/comments/**").authenticated()

                        // Admin & User specific
                        .requestMatchers("/admin/logs/**").authenticated()

                        // User endpoints
                        .requestMatchers("/user/**").authenticated()

                        // General admin rules
                        // Used to populate approver pickers; every caller sends a JWT,
                        // so this must not be public - it exposes the full user list.
                        .requestMatchers(HttpMethod.GET, "/api/users").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/users/me").authenticated()

                        // Strict Admin endpoints (Now protected via @PreAuthorize at method level)
                        .requestMatchers("/admin/**").authenticated()

                        // Other APIs require authentication
                        .anyRequest().authenticated()
                )
                .exceptionHandling(handling -> handling
                        .accessDeniedHandler(auditingAccessDeniedHandler()))

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // Add JWT filter before Spring's authentication filter
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // Password encoder using BCrypt hashing
    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}

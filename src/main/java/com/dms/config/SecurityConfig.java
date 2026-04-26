package com.dms.config;

import com.dms.security.JwtFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
<<<<<<< HEAD

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
=======
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.config.Customizer;
>>>>>>> origin/dev
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;

import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.Customizer;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    // ✅ MAIN SECURITY CONFIG
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .cors(Customizer.withDefaults()) // ✅ Enable CORS properly
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // ✅ PUBLIC APIs (VERY IMPORTANT for your frontend)
                        .requestMatchers("/auth/**").permitAll()
<<<<<<< HEAD
                        .requestMatchers("/api/search/**").permitAll()
                        .requestMatchers("/api/documents/**").permitAll()
                        .requestMatchers("/api/folders/**").permitAll()
                        .requestMatchers("/api/tags/**").permitAll()
                        .requestMatchers("/api/metadata/**").permitAll()
                        .requestMatchers("/api/workflows/**").permitAll()
                        .requestMatchers("/error").permitAll()

                        // ✅ ROLE BASED
                        .requestMatchers("/admin/**").hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/user/**").hasAnyRole("USER", "SYSTEM_ADMIN")

                        .anyRequest().authenticated()
                )

                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                // ✅ JWT FILTER
=======

                        .requestMatchers(HttpMethod.POST, "/api/share-links").authenticated() 
                        .requestMatchers(HttpMethod.POST, "/api/share-links/*/access").permitAll() 
                        .requestMatchers(HttpMethod.DELETE, "/api/share-links/**").authenticated() 
                        .requestMatchers(HttpMethod.GET, "/api/share-links/**").permitAll()

                        .requestMatchers("/api/comments/**").permitAll()

                        .requestMatchers("/admin/**").hasRole("SYSTEM_ADMIN")
                        .requestMatchers("/user/").hasAnyRole("USER", "SYSTEM_ADMIN")

                        .anyRequest().authenticated()
                )
                .httpBasic(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
>>>>>>> origin/dev
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();

    }

    // ✅ CORS CONFIG (VERY IMPORTANT FOR FRONTEND)
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(List.of("http://localhost:3000")); // your frontend
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    // ✅ PASSWORD ENCODER
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // ✅ AUTH MANAGER
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
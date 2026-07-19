package com.thestars.chatbox.config;

import com.thestars.chatbox.security.CustomUserDetailsService;
import com.thestars.chatbox.security.TokenAuthenticationFilter;
import com.thestars.chatbox.service.UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.DispatcherType;

/**
 * Security configuration with custom token-based authentication.
 * Enables multi-tab isolated sessions.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final UserService userService;
    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(UserService userService, CustomUserDetailsService userDetailsService) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for token-based APIs
            .csrf(csrf -> csrf.disable())

            // Register token-based authentication filter
            .addFilterBefore(new TokenAuthenticationFilter(userService, userDetailsService),
                    UsernamePasswordAuthenticationFilter.class)

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Allow JSP forwards
                .dispatcherTypeMatchers(DispatcherType.FORWARD).permitAll()
                // Public paths and resources
                .requestMatchers(
                    "/",
                    "/login",
                    "/api/auth/login",
                    "/api/auth/register",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/uploads/**",
                    "/favicon.ico",
                    "/ws/**"
                ).permitAll()
                // Everything else requires token authentication
                .anyRequest().authenticated()
            );

        return http.build();
    }
}

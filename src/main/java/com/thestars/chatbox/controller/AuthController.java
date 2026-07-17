package com.thestars.chatbox.controller;

import com.thestars.chatbox.model.User;
import com.thestars.chatbox.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;

/**
 * Handles authentication-related endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UserService userService, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String password = payload.get("password");

        Optional<User> userOpt = userService.findByEmail(email);
        if (userOpt.isEmpty() || !passwordEncoder.matches(password, userOpt.get().getPassword())) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid email or password"));
        }

        // Set status to ONLINE
        userService.setOnline(userOpt.get().getId());
        userOpt.get().setStatus("ONLINE");

        String token = userService.createSessionToken(email);
        return ResponseEntity.ok(Map.of("token", token, "user", userOpt.get()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            Optional<String> emailOpt = userService.getEmailByToken(token);
            if (emailOpt.isPresent()) {
                userService.findByEmail(emailOpt.get()).ifPresent(u -> userService.setOffline(u.getId()));
            }
            userService.removeSessionToken(token);
        }
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody Map<String, String> payload) {
        String email = payload.get("email");
        String displayName = payload.get("displayName");
        String team = payload.get("team");
        String password = payload.get("password");

        if (email == null || displayName == null || team == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "All fields are required"));
        }

        if (displayName.length() > 70) {
            return ResponseEntity.badRequest().body(Map.of("error", "Full name must be under 70 characters"));
        }

        if (userService.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Email is already registered"));
        }

        User user = User.builder()
                .email(email)
                .displayName(displayName)
                .team(team)
                .password(passwordEncoder.encode(password))
                .avatar("https://ui-avatars.com/api/?name=" + java.net.URLEncoder.encode(displayName, java.nio.charset.StandardCharsets.UTF_8) + "&background=3b82f6&color=fff&size=80&bold=true&format=svg")
                .role("USER")
                .status("OFFLINE")
                .build();

        User savedUser = userService.save(user);
        return ResponseEntity.ok(savedUser);
    }

    /**
     * Get the currently authenticated user's profile.
     * Called by the frontend after page load to get user info.
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(Principal principal,
                                            HttpServletRequest request) {
        if (principal == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Optional<User> user = userService.findByEmail(principal.getName());
        if (user.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        // Update last login IP
        String ip = getClientIp(request);
        userService.updateLastLoginIp(user.get().getId(), ip);

        return ResponseEntity.ok(user.get());
    }

    /**
     * Extract client IP address, handling proxy headers.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}

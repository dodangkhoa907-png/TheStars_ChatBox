package com.thestars.chatbox.security;

import com.thestars.chatbox.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Filter that intercepts incoming requests and authenticates the user
 * if a valid session token is provided in the Authorization header or token query parameter.
 */
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final UserService userService;
    private final CustomUserDetailsService userDetailsService;

    public TokenAuthenticationFilter(UserService userService, CustomUserDetailsService userDetailsService) {
        this.userService = userService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = null;

        // Try reading from Header
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }

        // Try reading from Query Parameter (useful for WebSocket connection handshake)
        if (token == null) {
            token = request.getParameter("token");
        }

        if (token != null) {
            Optional<String> emailOpt = userService.getEmailByToken(token);
            if (emailOpt.isPresent()) {
                String email = emailOpt.get();
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(email);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (Exception e) {
                    logger.error("Could not set user authentication in security context", e);
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}

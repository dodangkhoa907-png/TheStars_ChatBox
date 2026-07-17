package com.thestars.chatbox.service;

import com.thestars.chatbox.dao.UserDAO;
import com.thestars.chatbox.model.User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Business logic for user operations.
 */
@Service
public class UserService {

    private final UserDAO userDAO;

    public UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    /**
     * Get the currently logged-in user from OAuth2 principal.
     */
    public Optional<User> getCurrentUser(OAuth2User oAuth2User) {
        if (oAuth2User == null) return Optional.empty();
        String googleId = oAuth2User.getAttribute("sub");
        return userDAO.findByGoogleId(googleId);
    }

    public Optional<User> findById(Long id) {
        return userDAO.findById(id);
    }

    public Optional<User> findByGoogleId(String googleId) {
        return userDAO.findByGoogleId(googleId);
    }

    public Optional<User> findByEmail(String email) {
        return userDAO.findByEmail(email);
    }

    public List<User> searchByName(String query) {
        return userDAO.searchByName(query);
    }

    public List<User> findAll() {
        return userDAO.findAll();
    }

    public User save(User user) {
        return userDAO.save(user);
    }

    public void updateLastLoginIp(Long userId, String ip) {
        userDAO.updateLastLoginIp(userId, ip);
    }

    public void setOnline(Long userId) {
        userDAO.updateStatus(userId, "ONLINE");
    }

    public void setOffline(Long userId) {
        userDAO.updateStatus(userId, "OFFLINE");
    }

    // ── Token Session Management for Multi-Tab Autonomy ──
    private final java.util.Map<String, String> tokenToEmailMap = new java.util.concurrent.ConcurrentHashMap<>();

    public String createSessionToken(String email) {
        String token = java.util.UUID.randomUUID().toString();
        tokenToEmailMap.put(token, email);
        return token;
    }

    public java.util.Optional<String> getEmailByToken(String token) {
        return java.util.Optional.ofNullable(tokenToEmailMap.get(token));
    }

    public void removeSessionToken(String token) {
        tokenToEmailMap.remove(token);
    }
}

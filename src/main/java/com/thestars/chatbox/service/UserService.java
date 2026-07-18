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

    private final java.util.Map<String, User> emailToUserCache = new java.util.concurrent.ConcurrentHashMap<>();

    public void clearCache(String email) {
        if (email != null) {
            emailToUserCache.remove(email);
        }
    }

    private void clearCacheByUserId(Long userId) {
        if (userId != null) {
            emailToUserCache.values().removeIf(user -> userId.equals(user.getId()));
        }
    }

    public Optional<User> findById(Long id) {
        return userDAO.findById(id);
    }

    public Optional<User> findByGoogleId(String googleId) {
        return userDAO.findByGoogleId(googleId);
    }

    public Optional<User> findByEmail(String email) {
        if (email == null) return Optional.empty();
        User cached = emailToUserCache.get(email);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<User> user = userDAO.findByEmail(email);
        user.ifPresent(u -> emailToUserCache.put(email, u));
        return user;
    }

    public List<User> searchByName(String query) {
        return userDAO.searchByName(query);
    }

    public List<User> findAll() {
        return userDAO.findAll();
    }

    public User save(User user) {
        User saved = userDAO.save(user);
        if (saved != null && saved.getEmail() != null) {
            emailToUserCache.put(saved.getEmail(), saved);
        }
        return saved;
    }

    public void updateLastLoginIp(Long userId, String ip) {
        userDAO.updateLastLoginIp(userId, ip);
        clearCacheByUserId(userId);
    }

    public void setOnline(Long userId) {
        userDAO.updateStatus(userId, "ONLINE");
        clearCacheByUserId(userId);
    }

    public void setOffline(Long userId) {
        userDAO.updateStatus(userId, "OFFLINE");
        clearCacheByUserId(userId);
    }

    /** Generic status setter — used for AWAY (idle) where there's no dedicated convenience method. */
    public void setStatus(Long userId, String status) {
        userDAO.updateStatus(userId, status);
        clearCacheByUserId(userId);
    }

    /** Update a user's own display name and/or avatar. Pass null for a field to leave it unchanged. */
    public User updateProfile(Long userId, String displayName, String avatar) {
        User current = userDAO.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        String newDisplayName = displayName != null ? displayName : current.getDisplayName();
        String newAvatar = avatar != null ? avatar : current.getAvatar();
        userDAO.updateProfile(userId, newDisplayName, newAvatar);
        clearCacheByUserId(userId);
        return userDAO.findById(userId).orElseThrow();
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

package com.thestars.chatbox.security;

import com.thestars.chatbox.dao.UserDAO;
import com.thestars.chatbox.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Custom OAuth2 User Service that maps Google profile data
 * to our Users table in the database.
 *
 * Called automatically after successful Google authentication.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger log = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    private final UserDAO userDAO;

    public CustomOAuth2UserService(UserDAO userDAO) {
        this.userDAO = userDAO;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oAuth2User.getAttributes();

        String googleId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");
        String name = (String) attributes.get("name");
        String avatar = (String) attributes.get("picture");

        log.info("OAuth2 login: {} ({})", email, googleId);

        // Check if user already exists in database
        Optional<User> existingUser = userDAO.findByGoogleId(googleId);

        if (existingUser.isPresent()) {
            // Update existing user's profile (name/avatar may have changed)
            User user = existingUser.get();
            user.setDisplayName(name);
            user.setAvatar(avatar);
            user.setEmail(email);
            user.setStatus("ONLINE");
            userDAO.update(user);
            log.info("Existing user logged in: {} (id={})", email, user.getId());
        } else {
            // Create new user
            User newUser = User.builder()
                    .googleId(googleId)
                    .email(email)
                    .displayName(name)
                    .avatar(avatar)
                    .role("USER")
                    .status("ONLINE")
                    .build();
            userDAO.save(newUser);
            log.info("New user created: {} (id={})", email, newUser.getId());
        }

        return oAuth2User;
    }
}

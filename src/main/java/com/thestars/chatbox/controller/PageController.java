package com.thestars.chatbox.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handles view/page routing.
 * Serves the main SPA page and the login page.
 */
@Controller
public class PageController {

    /**
     * Main chat application page.
     * Redirects to login if not authenticated (handled by Spring Security).
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Login page with Google OAuth option.
     */
    @GetMapping("/login")
    public String login() {
        return "login";
    }
}

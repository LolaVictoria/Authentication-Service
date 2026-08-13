package com.authserver.auth_server.auth.user;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.authserver.auth_server.user.User;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public String me(Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        return "Hello " + user.getName()
                + ", your email is " + user.getEmail();
    }
}
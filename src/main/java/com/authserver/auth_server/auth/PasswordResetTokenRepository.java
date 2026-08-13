package com.authserver.auth_server.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.authserver.auth_server.user.User;

public interface PasswordResetTokenRepository
        extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByToken(String token);

    void deleteAllByUser(
            User user
    );
    Optional<PasswordResetToken> findByUser(User user);
}
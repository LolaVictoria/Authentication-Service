package com.authserver.auth_server.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.authserver.auth_server.user.User;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, Long> {

     Optional<VerificationToken> findByToken(String token);
     void deleteAllByUser(User user);
}

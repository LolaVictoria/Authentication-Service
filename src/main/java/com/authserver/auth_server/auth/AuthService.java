package com.authserver.auth_server.auth;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.authserver.auth_server.auth.dto.LoginRequest;
import com.authserver.auth_server.auth.dto.LogoutRequest;
import com.authserver.auth_server.auth.dto.RefreshRequest;
import com.authserver.auth_server.auth.dto.RegisterRequest;
import com.authserver.auth_server.auth.dto.ResetPasswordRequest;
import com.authserver.auth_server.auth.dto.VerifyAccountRequest;
import com.authserver.auth_server.exception.AuthException;
import com.authserver.auth_server.user.AccountStatus;
import com.authserver.auth_server.user.Role;
import com.authserver.auth_server.user.User;
import com.authserver.auth_server.user.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.authserver.auth_server.auth.dto.AuthResponse;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    @Value("${verification.token.expiration-hours}")
    private long verificationTokenExpirationHours;
    @Value("${password.reset.token.expiration-hours}")
    private long passwordResetTokenExpirationHours;
    @Value("${refresh.token.expiration-days}")
    private long refreshTokenExpirationDays;

    public AuthService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtService jwtService,
        RefreshTokenRepository refreshTokenRepository,
        VerificationTokenRepository verificationTokenRepository,
        PasswordResetTokenRepository passwordResetTokenRepository
    )  {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.verificationTokenRepository = verificationTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }


    private String generateRefreshToken() {

        byte[] randomBytes = new byte[32];

        secureRandom.nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private String hashToken(String token) {

        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    token.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            return Base64.getEncoder()
                    .encodeToString(hash);

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "SHA-256 algorithm not available", e
            );
        }
    }


    private void revokeFamily(String familyId) {

        List<RefreshToken> tokens =
                refreshTokenRepository.findAllByFamilyId(familyId);

        LocalDateTime now = LocalDateTime.now();

        for (RefreshToken token : tokens) {

            if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
            }
        }

        refreshTokenRepository.saveAll(tokens);
    }

    public User register(RegisterRequest request) {
        // Check if the email is already registered before creating a new user
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new AuthException("Email already registered");
        }
        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());

        user.setPassword(
            passwordEncoder.encode(request.getPassword())

        );
        user.setRole(Role.USER);
        user.setStatus(AccountStatus.PENDING_VERIFICATION);
        
        LocalDateTime now = LocalDateTime.now();

        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        User savedUser = userRepository.save(user);

        String verificationToken = generateRefreshToken();
        VerificationToken verificationTokenEntity = new VerificationToken();

        verificationTokenEntity.setToken(hashToken(verificationToken));
        verificationTokenEntity.setUser(savedUser);

        verificationTokenEntity.setCreatedAt(LocalDateTime.now());

        verificationTokenEntity.setExpiresAt(LocalDateTime.now().plusHours(verificationTokenExpirationHours));

        verificationTokenRepository.save(
                verificationTokenEntity
        );

        return savedUser;

    }   

    public AuthResponse login(LoginRequest request) {
        
        User user = userRepository.findByEmail(request.getEmail())
        .orElseThrow(() -> new AuthException("Invalid credentials"));

        if (!passwordEncoder.matches(
            request.getPassword(),
            user.getPassword()
        )) {
                throw new AuthException("Invalid credentials");
        }

        if (user.getStatus() != AccountStatus.ACTIVE) {
                throw new AuthException("Account is not verified");
        }

        String accessToken = jwtService.generateToken(user.getEmail());

        String refreshToken = generateRefreshToken();
        String familyId = java.util.UUID.randomUUID().toString();
        RefreshToken refreshTokenEntity = new RefreshToken();

        refreshTokenEntity.setTokenHash(hashToken(refreshToken));
        refreshTokenEntity.setUser(user);
        refreshTokenEntity.setFamilyId(familyId);
        refreshTokenEntity.setCreatedAt(LocalDateTime.now());
        refreshTokenEntity.setExpiresAt(
                LocalDateTime.now().plusDays(refreshTokenExpirationDays)
        );

        refreshTokenRepository.save(refreshTokenEntity);
        return new AuthResponse(
            accessToken,
            refreshToken
        );
        // return jwtService.generateToken(user.getEmail());

    }

    private void revokeFamilyTokensForUser(User user) {

        List<RefreshToken> tokens =
                refreshTokenRepository.findAllByUser(user);

        LocalDateTime now = LocalDateTime.now();

        for (RefreshToken token : tokens) {

                if (token.getRevokedAt() == null) {
                token.setRevokedAt(now);
                }
        }

         refreshTokenRepository.saveAll(tokens);
        }


        @Transactional
        public AuthResponse refresh(RefreshRequest request) {

                String rawRefreshToken = request.getRefreshToken();

                String tokenHash = hashToken(rawRefreshToken);

                RefreshToken existingToken = refreshTokenRepository
                        .findByTokenHash(tokenHash)
                        .orElseThrow(() ->
                                new AuthException("Invalid refresh token"));

                /*
                * If this token was already revoked, somebody is trying
                * to reuse an old refresh token.
                */
                if (existingToken.getRevokedAt() != null) {

                        revokeFamily(existingToken.getFamilyId());

                        throw new AuthException(
                                "Refresh token reuse detected"
                        );
                }

                /*
                * Normal expiration.
                */
                if (existingToken.getExpiresAt()
                        .isBefore(LocalDateTime.now())) {

                        throw new AuthException(
                                "Refresh token has expired"
                        );
                }

                User user = existingToken.getUser();

                /*
                * The old token is no longer usable.
                */
                existingToken.setRevokedAt(
                        LocalDateTime.now()
                );

                /*
                * Create replacement refresh token.
                */
                String newRefreshToken =
                        generateRefreshToken();

                RefreshToken newToken =
                        new RefreshToken();

                newToken.setTokenHash(
                        hashToken(newRefreshToken)
                );

                newToken.setUser(user);

                /*
                * Same family as the old token.
                */
                newToken.setFamilyId(
                        existingToken.getFamilyId()
                );

                newToken.setCreatedAt(
                        LocalDateTime.now()
                );

                newToken.setExpiresAt(
                        LocalDateTime.now().plusDays(30)
                );

                /*
                * IMPORTANT:
                * Save the new token FIRST.
                */
                RefreshToken savedNewToken =
                        refreshTokenRepository.save(newToken);

                /*
                * Now link old → new.
                */
                existingToken.setReplacedBy(savedNewToken);

                /*
                * Save the revoked old token.
                */
                refreshTokenRepository.save(existingToken);

                /*
                * New access token.
                */
                String newAccessToken =
                        jwtService.generateToken(
                                user.getEmail()
                        );

                return new AuthResponse(
                        newAccessToken,
                        newRefreshToken
                );
        }

        @Transactional
        public void verifyAccount(VerifyAccountRequest request) {
                String tokenHash = hashToken(request.getToken());

                VerificationToken verificationToken = 
                        verificationTokenRepository
                                .findByToken(tokenHash)
                                .orElseThrow(() ->
                                        new AuthException(
                                                "Invalid verification token"
                                        )
                                );

                        if (verificationToken.getVerifiedAt() != null) {
                                throw new AuthException(
                                        "verification token has already been used"
                                );
                        }

                        if (verificationToken.getExpiresAt()
                                .isBefore(LocalDateTime.now())) {

                                throw new AuthException(
                                        "Verification token has expired"
                                );
                        }

                         User user = verificationToken.getUser();

                        user.setStatus(AccountStatus.ACTIVE);
                        user.setUpdatedAt(LocalDateTime.now());

                        verificationToken.setVerifiedAt(
                                LocalDateTime.now()
                        );

                        userRepository.save(user);
                        verificationTokenRepository.save(
                                verificationToken
                        );

        }


        @Transactional
        public void logout(LogoutRequest request) {

                String rawRefreshToken = request.getRefreshToken();

                String tokenHash = hashToken(rawRefreshToken);

                RefreshToken existingToken =
                        refreshTokenRepository
                                .findByTokenHash(tokenHash)
                                .orElseThrow(() ->
                                        new AuthException("Invalid refresh token"));

                if (existingToken.getRevokedAt() != null) {
                        throw new AuthException("Refresh token already revoked");
                }

                existingToken.setRevokedAt(
                        LocalDateTime.now()
                );

                refreshTokenRepository.save(existingToken);
        }

        @Transactional
        public String resendVerification(String email) {

                User user = userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new AuthException("Invalid email")
                        );

                if (user.getStatus() == AccountStatus.ACTIVE) {
                        throw new AuthException(
                                "Account is already verified"
                        );
                }

                // Remove old verification tokens
                verificationTokenRepository.deleteAllByUser(user);

                // Generate a new token
                String verificationToken = generateRefreshToken();

                VerificationToken verificationTokenEntity =
                        new VerificationToken();

                verificationTokenEntity.setToken(verificationToken);
                verificationTokenEntity.setUser(user);
                verificationTokenEntity.setCreatedAt(
                        LocalDateTime.now()
                );
                verificationTokenEntity.setExpiresAt(
                        LocalDateTime.now()
                                .plusHours(verificationTokenExpirationHours)
                );

                verificationTokenRepository.save(
                        verificationTokenEntity
                );

                return verificationToken;
        }

        @Transactional
        public String forgotPassword(String email) {
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new AuthException("Invalid email")
                        );

                // Remove any previous reset tokens
                passwordResetTokenRepository
                        .findByUser(user)
                        .ifPresent(existingToken -> {
                        passwordResetTokenRepository.delete(existingToken);
                        passwordResetTokenRepository.flush();
                        });

                // Generate a new secure reset token
                String resetToken = generateRefreshToken();

                PasswordResetToken resetTokenEntity =
                        new PasswordResetToken();

                resetTokenEntity.setToken(hashToken(resetToken));
                resetTokenEntity.setUser(user);

                resetTokenEntity.setCreatedAt(
                        LocalDateTime.now()
                );

                resetTokenEntity.setExpiresAt(
                        LocalDateTime.now()
                                .plusHours(passwordResetTokenExpirationHours)
                );

                passwordResetTokenRepository.save(
                        resetTokenEntity
                );

                // Temporary for development/testing.
                // Later this token will be sent by email instead.
                return resetToken;
        }


        @Transactional
                public void resetPassword(ResetPasswordRequest request) {
                
                String tokenHash = hashToken(request.getToken());
                PasswordResetToken resetToken =
                        passwordResetTokenRepository
                                .findByToken(tokenHash)
                                .orElseThrow(() ->
                                        new AuthException(
                                                "Invalid password reset token"
                                        )
                                );

                // Token has already been used
                if (resetToken.getUsedAt() != null) {
                        throw new AuthException(
                                "Password reset token has already been used"
                        );
                }

                // Token has expired
                if (resetToken.getExpiresAt()
                        .isBefore(LocalDateTime.now())) {

                        throw new AuthException(
                                "Password reset token has expired"
                        );
                }

                User user = resetToken.getUser();

                // Hash the new password before storing it
                user.setPassword(
                        passwordEncoder.encode(
                                request.getNewPassword()
                        )
                );

                user.setUpdatedAt(LocalDateTime.now());

                // Mark token as used
                resetToken.setUsedAt(LocalDateTime.now());

                userRepository.save(user);

                passwordResetTokenRepository.save(resetToken);

                // Invalidate existing refresh tokens
                revokeFamilyTokensForUser(user);
        }
}

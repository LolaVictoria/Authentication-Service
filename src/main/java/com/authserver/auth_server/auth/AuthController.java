package com.authserver.auth_server.auth;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.authserver.auth_server.auth.dto.LoginRequest;
import com.authserver.auth_server.auth.dto.LogoutRequest;
import com.authserver.auth_server.auth.dto.RefreshRequest;
import com.authserver.auth_server.auth.dto.RegisterRequest;
import com.authserver.auth_server.auth.dto.ResendVerificationRequest;
import com.authserver.auth_server.auth.dto.ResetPasswordRequest;
import com.authserver.auth_server.auth.dto.VerifyAccountRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import com.authserver.auth_server.auth.dto.AuthResponse;
import com.authserver.auth_server.auth.dto.ForgotPasswordRequest;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authservice) {
        this.authService = authservice;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        authService.register(request);
        
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestBody RefreshRequest request
    ) {
        AuthResponse response = authService.refresh(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(response);
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyAccount(
            @RequestBody VerifyAccountRequest request
    ) {
        authService.verifyAccount(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Account verified successfully");
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @RequestBody LogoutRequest request
    ) {
        authService.logout(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Logged out successfully");
    }


    @PostMapping("/resend-verification")
     public ResponseEntity<?> resendVerification(
                @RequestBody ResendVerificationRequest request
        ) {

        String token = authService.resendVerification(
                request.getEmail()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(token);
        }

        @PostMapping("/forgot-password")
        public ResponseEntity<?> forgotPassword(
                @RequestBody ForgotPasswordRequest request
        ) {

        String token = authService.forgotPassword(
                request.getEmail()
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(token);
        }

        @PostMapping("/reset-password")
        public ResponseEntity<?> resetPassword(
                @RequestBody ResetPasswordRequest request
        ) {

        authService.resetPassword(request);

        return ResponseEntity
                .status(HttpStatus.OK)
                .body("Password reset successfully");
        }
        
}

package com.authserver.auth_server.exception;

import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ErrorResponse> handleAuthException(
            AuthException exception
    ) {

        HttpStatus status = HttpStatus.UNAUTHORIZED;

        ErrorResponse errorResponse =
                new ErrorResponse(
                        status.value(),
                        exception.getMessage(),
                        LocalDateTime.now()
                );

        return ResponseEntity
                .status(status)
                .body(errorResponse);
    }
}
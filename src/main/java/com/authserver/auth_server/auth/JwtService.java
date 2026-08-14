package com.authserver.auth_server.auth;

import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    
    private final SecretKey secretKey;
    private final long accessTokenExpirationHours;
    
    public JwtService(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-token.expiration-hours}") long accessTokenExpirationHours) {
        this.secretKey = Keys.hmacShaKeyFor(
            Decoders.BASE64.decode(secret)
        );
        this.accessTokenExpirationHours = accessTokenExpirationHours;
    }

    public String generateToken(String email) {

        Date now = new Date();

        Date expiration = new Date(
                now.getTime() + accessTokenExpirationHours * 1000 * 60 * 60
        );

        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(secretKey)
                .compact();
    }

    public String extractEmail(String token) {

        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
package com.authserver.auth_server.auth.dto;

public class VerifyAccountRequest {

    private String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
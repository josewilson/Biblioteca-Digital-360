package br.com.biblioteca.security;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}

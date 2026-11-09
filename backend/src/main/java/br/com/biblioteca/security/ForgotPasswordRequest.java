package br.com.biblioteca.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForgotPasswordRequest(@NotBlank @Size(max = 254) String email) {
}

package br.com.biblioteca.user;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(min = 8, max = 72) String password,   // 72 = limite do BCrypt
        @Pattern(regexp = "\\d{11}", message = "CPF deve ter 11 dígitos") String cpf,
        @Size(max = 30) String registration,
        @Size(max = 20) String phone,
        @Size(max = 250) String address,
        @NotEmpty Set<String> roles) {
}

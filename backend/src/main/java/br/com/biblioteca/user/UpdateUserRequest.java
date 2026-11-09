package br.com.biblioteca.user;

import java.util.Set;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** cpf: null mantém o atual; vazio apaga (o CPF nunca sai na API, então o formulário não o conhece). */
public record UpdateUserRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 150) String email,
        @Pattern(regexp = "^(\\d{11})?$", message = "CPF deve ter 11 dígitos") String cpf,
        @Size(max = 30) String registration,
        @Size(max = 20) String phone,
        @NotEmpty Set<String> roles) {
}

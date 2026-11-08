package br.com.biblioteca.user;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/** Nunca inclui hash de senha nem CPF. */
public record UserResponse(Long id, String fullName, String email, String registration, String phone,
                           boolean active, Set<String> roles, Instant createdAt) {

    static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRegistration(), u.getPhone(),
                u.isActive(), u.getRoles().stream().map(Role::getCode).collect(Collectors.toSet()), u.getCreatedAt());
    }
}

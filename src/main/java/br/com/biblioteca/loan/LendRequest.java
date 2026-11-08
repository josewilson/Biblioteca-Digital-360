package br.com.biblioteca.loan;

import jakarta.validation.constraints.NotNull;

public record LendRequest(@NotNull Long userId, @NotNull Long copyId) {
}

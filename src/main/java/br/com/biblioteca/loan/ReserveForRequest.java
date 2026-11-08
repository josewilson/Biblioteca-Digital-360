package br.com.biblioteca.loan;

import jakarta.validation.constraints.NotNull;

/** Reserva feita pela equipe em nome de um leitor. */
public record ReserveForRequest(@NotNull Long userId, @NotNull Long bookId) {
}

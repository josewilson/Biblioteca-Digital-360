package br.com.biblioteca.loan;

import jakarta.validation.constraints.NotNull;

/** Pedido do próprio leitor (o leitor vem do token). */
public record ReserveRequest(@NotNull Long bookId) {
}

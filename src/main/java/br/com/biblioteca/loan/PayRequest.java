package br.com.biblioteca.loan;

import jakarta.validation.constraints.NotNull;

/** O valor não vem no pedido: é sempre o valor integral da multa (sem pagamento parcial). */
public record PayRequest(@NotNull PaymentMethod method) {
}

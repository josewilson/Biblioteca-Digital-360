package br.com.biblioteca.loan;

/** Resultado da devolução; {@code fine} é nulo quando não há multa. */
public record ReturnResponse(LoanResponse loan, FineResponse fine) {
}

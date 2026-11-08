package br.com.biblioteca.loan;

/** Forma de pagamento registrada no balcão. Não há integração com provedor financeiro. */
public enum PaymentMethod {
    DINHEIRO, PIX, CARTAO_DEBITO, CARTAO_CREDITO
}

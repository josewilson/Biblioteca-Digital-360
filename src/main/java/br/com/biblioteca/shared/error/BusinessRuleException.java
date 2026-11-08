package br.com.biblioteca.shared.error;

/** Violação de regra de negócio (vira HTTP 422). A mensagem é mostrada ao usuário. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}

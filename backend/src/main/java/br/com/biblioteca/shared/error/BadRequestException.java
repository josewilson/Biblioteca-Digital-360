package br.com.biblioteca.shared.error;

/** Entrada malformada que não passa por Bean Validation (ex.: parâmetro de URL). Vira 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}

package br.com.biblioteca.shared.error;

/** Excesso de tentativas (vira HTTP 429). */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}

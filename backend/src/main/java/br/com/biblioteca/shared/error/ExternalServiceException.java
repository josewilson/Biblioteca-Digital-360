package br.com.biblioteca.shared.error;

/** Serviço de terceiros fora do ar, lento ou com resposta inútil. Vira 503; quem chama pode seguir sem ele. */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message) {
        super(message);
    }

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}

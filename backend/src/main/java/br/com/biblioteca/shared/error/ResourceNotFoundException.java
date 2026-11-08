package br.com.biblioteca.shared.error;

/** Recurso inexistente (vira HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}

package br.com.biblioteca.shared.error;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void regraDeNegocioViraHttp422ComAMensagem() {
        ProblemDetail p = handler.businessRule(new BusinessRuleException("Limite de empréstimos atingido."));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), p.getStatus());
        assertEquals("Limite de empréstimos atingido.", p.getDetail());
    }

    @Test
    void recursoInexistenteViraHttp404() {
        ProblemDetail p = handler.notFound(new ResourceNotFoundException("Livro 9 não existe."));
        assertEquals(HttpStatus.NOT_FOUND.value(), p.getStatus());
        assertTrue(p.getDetail().contains("Livro 9"));
    }
}

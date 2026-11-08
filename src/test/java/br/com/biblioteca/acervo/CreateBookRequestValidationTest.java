package br.com.biblioteca.acervo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CreateBookRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private CreateBookRequest withCover(String coverUrl) {
        return new CreateBookRequest("Dom Casmurro", null, null, null, null, null, coverUrl, null, null, null,
                List.of(new CopyRequest("A1", CopyKind.CONSULTA, null, null), new CopyRequest("A2", CopyKind.CIRCULANTE, null, null)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://exemplo.org/capa.png", "http://exemplo.org/capa.jpg?x=1", ""})
    void aceitaEnderecoHttpHttpsOuVazio(String cover) {
        assertTrue(validator.validate(withCover(cover)).isEmpty());
    }

    @Test
    void aceitaSemCapa() {
        assertTrue(validator.validate(withCover(null)).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "data:text/html;base64,PHNjcmlwdD4=", "ftp://exemplo.org/capa.png",
            "//exemplo.org/capa.png", "https://exemplo.org/capa com espaço.png", "file:///etc/passwd"})
    void recusaEsquemasPerigososOuMalformados(String cover) {
        var violations = validator.validate(withCover(cover));
        assertEquals(1, violations.size());
        assertEquals("coverUrl", violations.iterator().next().getPropertyPath().toString());
    }
}

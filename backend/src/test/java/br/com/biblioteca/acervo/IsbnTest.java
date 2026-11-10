package br.com.biblioteca.acervo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import br.com.biblioteca.shared.error.BadRequestException;

class IsbnTest {

    @Test
    void normalizaIsbn13ComHifenEEspaco() {
        assertEquals("9788535914849", Isbn.normalize("9788535914849"));
        assertEquals("9788535914849", Isbn.normalize(" 978-85-359-1484-9 "));
    }

    @Test
    void converteIsbn10ParaIsbn13() {
        assertEquals("9788535914849", Isbn.normalize("8535914846"));
        assertEquals("9788535914849", Isbn.normalize("85-359-1484-6"));
        assertEquals("9780804429573", Isbn.normalize("080442957x"));   // verificador X, em minúscula
    }

    @Test
    void recusaVerificadorErradoTamanhoErradoELetras() {
        for (String ruim : new String[] {"9788535914840", "8535914847", "123", "", "97885359148490", "abcdefghijklm", "978853591484X"}) {
            assertThrows(BadRequestException.class, () -> Isbn.normalize(ruim), ruim);
        }
        assertThrows(BadRequestException.class, () -> Isbn.normalize(null));
    }

    @Test
    void daIsbn10DoIsbn13() {
        assertEquals("8535914846", Isbn.toIsbn10("9788535914849"));
        assertEquals("080442957X", Isbn.toIsbn10("9780804429573"));
        assertNull(Isbn.toIsbn10("9791234567896"));   // prefixo 979 não tem ISBN-10
    }
}

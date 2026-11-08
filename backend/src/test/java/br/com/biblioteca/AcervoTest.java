package br.com.biblioteca;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AcervoTest {

    @Test
    void oCatalogoAceitaTodoTipoDeItem() {
        List<ItemDoAcervo> catalogo = List.of(
                new Livro("L-001", "Dom Casmurro"),
                new ObraDeConsulta("C-001", "Dicionário Aurélio"),
                new EbookLivre("E-001", "Iracema", "https://exemplo.org/iracema.pdf"));

        assertEquals(3, catalogo.size());
        assertEquals("Dicionário Aurélio", catalogo.get(1).getTitulo());
    }

    @Test
    void oEbookLivreSeBaixa() {
        Baixavel ebook = new EbookLivre("E-001", "Iracema", "https://exemplo.org/iracema.pdf");
        assertEquals("https://exemplo.org/iracema.pdf", ebook.baixarPdf());
    }
}


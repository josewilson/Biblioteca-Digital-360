package br.com.biblioteca.acervo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.biblioteca.shared.error.ExternalServiceException;

/** Open Library sem rede: o segundo passo (nome dos autores) e seus limites. */
class OpenLibrarySourceTest {

    private static final String ISBN = "9780140449136";
    private static final String TEMPLATE = "https://openlibrary.org/isbn/%s.json";

    /** Responde por caminho; caminho ausente = 404; valor nulo = falha do provedor. */
    private static final class FakeHttp extends TrustedHttp {
        final Map<String, String> docs;
        final List<String> asked = new ArrayList<>();

        FakeHttp(Map<String, String> docs) {
            this.docs = docs;
        }

        @Override
        public Optional<byte[]> get(URI start, int maxBytes, Duration timeout, int... alsoNotFound) {
            asked.add(start.getPath());
            if (docs.containsKey(start.getPath()) && docs.get(start.getPath()) == null) {
                throw new ExternalServiceException("fora do ar", null);
            }
            return Optional.ofNullable(docs.get(start.getPath())).map(s -> s.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static OpenLibrarySource source(FakeHttp http) {
        return new OpenLibrarySource(http, TEMPLATE, Duration.ofSeconds(1));
    }

    private static final String EDITION_WITH_AUTHORS =
            "{\"title\":\"Crime and punishment\",\"authors\":[{\"key\":\"/authors/OL1A\"},{\"key\":\"/authors/OL2A\"}]}";

    @Test
    void pegaOsNomesDosAutoresDaEdicao() {
        FakeHttp http = new FakeHttp(Map.of(
                "/isbn/" + ISBN + ".json", EDITION_WITH_AUTHORS,
                "/authors/OL1A.json", "{\"name\":\"Fiódor Dostoiévski\"}",
                "/authors/OL2A.json", "{\"name\":\"Constance Garnett\"}"));

        BookData data = source(http).find(ISBN).orElseThrow();

        assertEquals(List.of("Fiódor Dostoiévski", "Constance Garnett"), data.authors());
        assertEquals("Crime and punishment", data.title());
    }

    @Test
    void semAutoresNaEdicaoBuscaNaObra() {
        FakeHttp http = new FakeHttp(Map.of(
                "/isbn/" + ISBN + ".json", "{\"title\":\"T\",\"works\":[{\"key\":\"/works/OL9W\"}]}",
                "/works/OL9W.json", "{\"authors\":[{\"author\":{\"key\":\"/authors/OL7A\"},\"type\":{\"key\":\"/type/author_role\"}}]}",
                "/authors/OL7A.json", "{\"name\":\"George Orwell\"}"));

        assertEquals(List.of("George Orwell"), source(http).find(ISBN).orElseThrow().authors());
    }

    @Test
    void falhaNoSegundoPassoNaoDerrubaAConsulta() {
        java.util.HashMap<String, String> docs = new java.util.HashMap<>();
        docs.put("/isbn/" + ISBN + ".json", EDITION_WITH_AUTHORS);
        docs.put("/authors/OL1A.json", null);                      // provedor fora do ar
        // OL2A.json não existe (404)
        BookData data = source(new FakeHttp(docs)).find(ISBN).orElseThrow();

        assertTrue(data.authors().isEmpty());
        assertEquals("Crime and punishment", data.title());
    }

    @Test
    void soSegueChavesNoFormatoEsperadoELimitaAQuantidade() {
        String refs = "[{\"key\":\"/authors/OL1A\"},{\"key\":\"//evil.example/x\"},{\"key\":\"/authors/../admin\"},"
                + "{\"key\":\"/authors/OL2A\"},{\"key\":\"/authors/OL3A\"},{\"key\":\"/authors/OL4A\"},{\"key\":\"/authors/OL5A\"},"
                + "{\"key\":\"/authors/OL6A\"},{\"key\":\"/authors/OL7A\"}]";
        java.util.HashMap<String, String> docs = new java.util.HashMap<>();
        docs.put("/isbn/" + ISBN + ".json", "{\"title\":\"T\",\"authors\":" + refs + "}");
        for (int i = 1; i <= 7; i++) {
            docs.put("/authors/OL" + i + "A.json", "{\"name\":\"Autor " + i + "\"}");
        }
        FakeHttp http = new FakeHttp(docs);

        BookData data = source(http).find(ISBN).orElseThrow();

        assertEquals(List.of("Autor 1", "Autor 2", "Autor 3", "Autor 4", "Autor 5"), data.authors());
        assertFalse(http.asked.stream().anyMatch(p -> p.contains("evil") || p.contains("admin")));
        assertEquals(6, http.asked.size());   // a edição + 5 autores
    }

    @Test
    void semReferenciaDeAutorNaoFazChamadaExtra() {
        FakeHttp http = new FakeHttp(Map.of("/isbn/" + ISBN + ".json", "{\"title\":\"T\"}"));

        assertTrue(source(http).find(ISBN).orElseThrow().authors().isEmpty());
        assertEquals(1, http.asked.size());
    }
}

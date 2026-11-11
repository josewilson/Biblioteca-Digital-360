package br.com.biblioteca.acervo;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.biblioteca.shared.error.ExternalServiceException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Complemento: Open Library (edição pelo ISBN). Traz idioma, descrição, subtítulo e capa, que a BrasilAPI não traz.
 * Os autores da edição são referências (`/authors/OL…A`): o nome vem de uma chamada extra por autor, e, se a edição não
 * os lista, da obra (`/works/OL…W`). Esse segundo passo é de melhor esforço: se falhar, a sugestão segue sem autores.
 */
@Component
@Order(2)
class OpenLibrarySource implements BookDataSource {

    private static final Pattern AUTHOR_KEY = Pattern.compile("/authors/OL\\d+A");
    private static final Pattern WORK_KEY = Pattern.compile("/works/OL\\d+W");
    /** No máximo tantos autores por obra: cada um custa uma chamada. */
    private static final int MAX_AUTHORS = 5;
    private static final Pattern YEAR = Pattern.compile("\\b(\\d{4})\\b");
    /** Idiomas mais comuns do acervo (códigos MARC da Open Library). Outro código: o campo fica em branco. */
    private static final Map<String, String> LANGUAGES = Map.ofEntries(
            Map.entry("por", "Português"), Map.entry("eng", "Inglês"), Map.entry("spa", "Espanhol"),
            Map.entry("fre", "Francês"), Map.entry("ger", "Alemão"), Map.entry("ita", "Italiano"),
            Map.entry("jpn", "Japonês"), Map.entry("lat", "Latim"), Map.entry("rus", "Russo"),
            Map.entry("chi", "Chinês"), Map.entry("kor", "Coreano"), Map.entry("ara", "Árabe"));

    private final TrustedHttp http;
    private final String urlTemplate;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    OpenLibrarySource(TrustedHttp http, @Value("${app.isbn.openlibrary-url}") String urlTemplate,
                      @Value("${app.isbn.timeout}") Duration timeout) {
        this.http = http;
        this.urlTemplate = urlTemplate;
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "openlibrary";
    }

    @Override
    public Optional<BookData> find(String isbn13) {
        URI start = URI.create(urlTemplate.formatted(isbn13));
        Optional<byte[]> body = http.get(start, 1_000_000, timeout);
        if (body.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode n = json.readTree(body.get());
            List<String> publishers = BookData.texts(n, "publishers");
            JsonNode cover = n.path("covers").path(0);
            return Optional.of(new BookData(
                    BookData.text(n, "title"), BookData.text(n, "subtitle"), authors(start, n),
                    publishers.isEmpty() ? null : publishers.get(0), year(BookData.text(n, "publish_date")),
                    language(n.path("languages").path(0).path("key").asString("")), BookData.text(n, "edition_name"),
                    description(n.path("description")),
                    cover.isNumber() && cover.asLong() > 0 ? "https://covers.openlibrary.org/b/id/" + cover.asLong() + "-L.jpg" : null,
                    BookData.texts(n, "subjects")));
        } catch (JacksonException e) {
            throw new ExternalServiceException("Resposta da Open Library ilegível.", e);
        }
    }

    /** Nomes dos autores: os da edição ou, na falta deles, os da obra. Só segue chaves no formato esperado. */
    private List<String> authors(URI start, JsonNode edition) {
        List<String> keys = authorKeys(edition.path("authors"), "key");
        if (keys.isEmpty()) {
            String work = edition.path("works").path(0).path("key").asString("");
            if (WORK_KEY.matcher(work).matches()) {
                keys = authorKeys(fetch(start, work).path("authors"), "author");
            }
        }
        return keys.stream().limit(MAX_AUTHORS).map(k -> BookData.text(fetch(start, k), "name"))
                .filter(java.util.Objects::nonNull).distinct().toList();
    }

    /** Chaves `/authors/OL…A` de uma lista de referências; `field` é "key" ou um objeto `{key}` ("author"). */
    private static List<String> authorKeys(JsonNode refs, String field) {
        if (!refs.isArray()) {
            return List.of();
        }
        return refs.valueStream()
                .map(r -> field.equals("key") ? r.path("key").asString("") : r.path(field).path("key").asString(""))
                .filter(k -> AUTHOR_KEY.matcher(k).matches()).toList();
    }

    /** Documento `<chave>.json` no mesmo servidor da consulta; qualquer falha vira "vazio" (o complemento é opcional). */
    private JsonNode fetch(URI start, String key) {
        try {
            Optional<byte[]> body = http.get(start.resolve(key + ".json"), 1_000_000, timeout);
            return body.isPresent() ? json.readTree(body.get()) : json.createObjectNode();
        } catch (ExternalServiceException | JacksonException e) {
            return json.createObjectNode();
        }
    }

    private static Integer year(String publishDate) {
        if (publishDate == null) {
            return null;
        }
        Matcher m = YEAR.matcher(publishDate);
        return m.find() ? Integer.valueOf(m.group(1)) : null;
    }

    private static String language(String key) {   // "/languages/por"
        return LANGUAGES.get(key.substring(key.lastIndexOf('/') + 1));
    }

    /** A descrição é texto simples ou um objeto com "value". */
    private static String description(JsonNode d) {
        String s = d.isString() ? d.asString() : d.path("value").asString("");
        return s.isBlank() ? null : s.trim();
    }
}

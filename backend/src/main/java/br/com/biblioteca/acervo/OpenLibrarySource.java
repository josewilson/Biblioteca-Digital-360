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
 * Os autores da edição são só referências (exigiriam outra chamada por autor), então ficam com a BrasilAPI.
 */
@Component
@Order(2)
class OpenLibrarySource implements BookDataSource {

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
        Optional<byte[]> body = http.get(URI.create(urlTemplate.formatted(isbn13)), 1_000_000, timeout);
        if (body.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode n = json.readTree(body.get());
            List<String> publishers = BookData.texts(n, "publishers");
            JsonNode cover = n.path("covers").path(0);
            return Optional.of(new BookData(
                    BookData.text(n, "title"), BookData.text(n, "subtitle"), List.of(),
                    publishers.isEmpty() ? null : publishers.get(0), year(BookData.text(n, "publish_date")),
                    language(n.path("languages").path(0).path("key").asString("")), BookData.text(n, "edition_name"),
                    description(n.path("description")),
                    cover.isNumber() && cover.asLong() > 0 ? "https://covers.openlibrary.org/b/id/" + cover.asLong() + "-L.jpg" : null,
                    BookData.texts(n, "subjects")));
        } catch (JacksonException e) {
            throw new ExternalServiceException("Resposta da Open Library ilegível.", e);
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

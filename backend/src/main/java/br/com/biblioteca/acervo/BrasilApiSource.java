package br.com.biblioteca.acervo;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.biblioteca.shared.error.ExternalServiceException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Fonte principal: BrasilAPI (agrega CBL, Open Library e outras; boa para livro brasileiro). Não traz idioma. */
@Component
@Order(1)
class BrasilApiSource implements BookDataSource {

    private final TrustedHttp http;
    private final String urlTemplate;
    private final Duration timeout;
    private final ObjectMapper json = new ObjectMapper();

    BrasilApiSource(TrustedHttp http, @Value("${app.isbn.brasilapi-url}") String urlTemplate,
                    @Value("${app.isbn.timeout}") Duration timeout) {
        this.http = http;
        this.urlTemplate = urlTemplate;
        this.timeout = timeout;
    }

    @Override
    public String name() {
        return "brasilapi";
    }

    @Override
    public Optional<BookData> find(String isbn13) {
        // A BrasilAPI responde 400 "ISBN inválido" para ISBN que ela não aceita (ex.: de fora do Brasil), mesmo com
        // verificador correto; para nós isso é "não conheço", não falha do serviço.
        Optional<byte[]> body = http.get(URI.create(urlTemplate.formatted(isbn13)), 1_000_000, timeout, 400);
        if (body.isEmpty()) {
            return Optional.empty();
        }
        try {
            JsonNode n = json.readTree(body.get());
            JsonNode year = n.path("year");
            return Optional.of(new BookData(
                    BookData.text(n, "title"), BookData.text(n, "subtitle"), BookData.texts(n, "authors"),
                    BookData.text(n, "publisher"), year.isNumber() ? year.asInt() : null, null, null,
                    BookData.text(n, "synopsis"), BookData.text(n, "cover_url"), BookData.texts(n, "subjects")));
        } catch (JacksonException e) {
            throw new ExternalServiceException("Resposta da BrasilAPI ilegível.", e);
        }
    }
}

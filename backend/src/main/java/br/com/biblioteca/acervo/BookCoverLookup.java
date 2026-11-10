package br.com.biblioteca.acervo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

/**
 * Busca a capa da obra pelo ISBN num serviço público (Open Library) e a grava pelo mesmo caminho do envio manual,
 * então passa pelas mesmas validações e é regravada em JPEG.
 *
 * Sem {@code @Transactional} de propósito: a chamada de rede não pode segurar uma conexão do banco.
 * Só o ISBN (que não é dado pessoal) sai do servidor. O endereço vem da configuração; um redirecionamento só é
 * seguido para o mesmo servidor, openlibrary.org ou archive.org, sempre por HTTPS (exceto o endereço configurado).
 */
@Service
public class BookCoverLookup {

    private static final Pattern ISBN = Pattern.compile("[0-9]{9}[0-9Xx]|[0-9]{13}");
    private static final int MAX_REDIRECTS = 3;

    private final BookRepository books;
    private final BookCoverService covers;
    private final String urlTemplate;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)   // os saltos são conferidos um a um, abaixo
            .build();

    BookCoverLookup(BookRepository books, BookCoverService covers,
                    @Value("${app.covers.lookup-url:}") String urlTemplate) {
        this.books = books;
        this.covers = covers;
        this.urlTemplate = urlTemplate.trim();
    }

    public BookResponse fetchAndStore(Long bookId, Long callerId, String ip) {
        if (urlTemplate.isEmpty()) {
            throw new BusinessRuleException("A busca automática de capa está desligada neste servidor. Envie a imagem.");
        }
        String isbn = books.findById(bookId).orElseThrow(() -> new ResourceNotFoundException("Obra " + bookId + " não existe."))
                .getIsbn();
        String digits = isbn == null ? "" : isbn.replaceAll("[\\s-]", "");
        if (!ISBN.matcher(digits).matches()) {
            throw new BusinessRuleException(digits.isEmpty()
                    ? "Informe o ISBN da obra (e salve) para buscar a capa."
                    : "O ISBN desta obra não é válido (10 ou 13 caracteres).");
        }
        byte[] image = download(URI.create(urlTemplate.formatted(digits)));
        if (image == null) {
            throw new BusinessRuleException("Nenhuma capa encontrada para o ISBN " + digits + ". Envie a imagem.");
        }
        return covers.store(bookId, image, callerId, ip);
    }

    /** @return os bytes, ou null se o serviço não tem capa para o ISBN (404) */
    private byte[] download(URI start) {
        URI uri = start;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                HttpResponse<InputStream> res = http.send(
                        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = res.body()) {
                    int status = res.statusCode();
                    if (status == 404) {
                        return null;
                    }
                    if (status / 100 == 3) {
                        uri = redirectTarget(start, uri, res.headers().firstValue("Location").orElse(""));
                        continue;
                    }
                    if (status != 200) {
                        throw unavailable();
                    }
                    byte[] data = body.readNBytes(BookCoverService.MAX_BYTES + 1);
                    if (data.length > BookCoverService.MAX_BYTES) {
                        throw new BusinessRuleException("A capa encontrada passa de 2 MB. Envie a imagem.");
                    }
                    return data;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException | IllegalArgumentException e) {
            throw unavailable();
        }
        throw unavailable();   // redirecionamentos demais
    }

    private URI redirectTarget(URI start, URI current, String location) {
        URI target = current.resolve(location);
        String host = target.getHost() == null ? "" : target.getHost().toLowerCase();
        boolean trusted = host.equals(start.getHost()) || host.equals("openlibrary.org") || host.endsWith(".openlibrary.org")
                || host.equals("archive.org") || host.endsWith(".archive.org");
        boolean secure = "https".equals(target.getScheme()) || target.getScheme() != null && target.getScheme().equals(start.getScheme());
        if (!trusted || !secure) {
            throw unavailable();
        }
        return target;
    }

    private static BusinessRuleException unavailable() {
        return new BusinessRuleException("Não foi possível consultar o serviço de capas agora. Tente depois ou envie a imagem.");
    }
}

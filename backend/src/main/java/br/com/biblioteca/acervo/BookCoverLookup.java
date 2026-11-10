package br.com.biblioteca.acervo;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ExternalServiceException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

/**
 * Busca a capa da obra pelo ISBN num serviço público (Open Library) e a grava pelo mesmo caminho do envio manual,
 * então passa pelas mesmas validações e é regravada em JPEG.
 *
 * Sem {@code @Transactional} de propósito: a chamada de rede não pode segurar uma conexão do banco.
 * Só o ISBN (que não é dado pessoal) sai do servidor. O endereço vem da configuração; os redirecionamentos são
 * controlados por {@link TrustedHttp}.
 */
@Service
public class BookCoverLookup {

    private static final Pattern ISBN = Pattern.compile("[0-9]{9}[0-9Xx]|[0-9]{13}");

    private final BookRepository books;
    private final BookCoverService covers;
    private final TrustedHttp http;
    private final String urlTemplate;

    BookCoverLookup(BookRepository books, BookCoverService covers, TrustedHttp http,
                    @Value("${app.covers.lookup-url:}") String urlTemplate) {
        this.books = books;
        this.covers = covers;
        this.http = http;
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
        Optional<byte[]> image;
        try {
            image = http.get(URI.create(urlTemplate.formatted(digits)), BookCoverService.MAX_BYTES, Duration.ofSeconds(10));
        } catch (ExternalServiceException e) {
            throw new BusinessRuleException("Não foi possível consultar o serviço de capas agora. Tente depois ou envie a imagem.");
        }
        return covers.store(bookId, image.orElseThrow(() ->
                new BusinessRuleException("Nenhuma capa encontrada para o ISBN " + digits + ". Envie a imagem.")), callerId, ip);
    }
}

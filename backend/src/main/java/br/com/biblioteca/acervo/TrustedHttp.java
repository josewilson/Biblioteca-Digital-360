package br.com.biblioteca.acervo;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

import org.springframework.stereotype.Component;

import br.com.biblioteca.shared.error.ExternalServiceException;

/**
 * GET para serviços públicos de livros (BrasilAPI, Open Library). Sem {@code @Transactional} em quem chama: a rede
 * não pode segurar conexão do banco. Redirecionamentos NUNCA são seguidos automaticamente: cada salto é conferido
 * (no máximo 3) e só vai para o mesmo servidor do endereço inicial, openlibrary.org ou archive.org, por HTTPS
 * (ou pelo mesmo esquema do endereço inicial, que vem da configuração). Isso impede usar o servidor para alcançar
 * a rede interna.
 */
@Component
public class TrustedHttp {

    private static final int MAX_REDIRECTS = 3;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * @param alsoNotFound outros status que o serviço usa para dizer "não conheço" (além do 404)
     * @return o corpo, ou vazio se o serviço respondeu 404 (ou um dos status de {@code alsoNotFound})
     * @throws ExternalServiceException em qualquer outra falha: rede, tempo, status, tamanho ou redirecionamento
     */
    public Optional<byte[]> get(URI start, int maxBytes, Duration timeout, int... alsoNotFound) {
        URI uri = start;
        try {
            for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
                HttpResponse<InputStream> res = http.send(
                        HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", "BibliotecaDigital360").GET().build(),
                        HttpResponse.BodyHandlers.ofInputStream());
                try (InputStream body = res.body()) {
                    int status = res.statusCode();
                    if (status == 404 || Arrays.stream(alsoNotFound).anyMatch(s -> s == status)) {
                        return Optional.empty();
                    }
                    if (status / 100 == 3) {
                        uri = redirectTarget(start, uri, res.headers().firstValue("Location").orElse(""));
                        continue;
                    }
                    if (status != 200) {
                        throw new ExternalServiceException("O serviço respondeu " + status + ".");
                    }
                    byte[] data = body.readNBytes(maxBytes + 1);
                    if (data.length > maxBytes) {
                        throw new ExternalServiceException("Resposta maior que o limite de " + maxBytes + " bytes.");
                    }
                    return Optional.of(data);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException("Consulta interrompida.", e);
        } catch (IOException | IllegalArgumentException e) {
            throw new ExternalServiceException("Falha de rede ou de tempo na consulta.", e);
        }
        throw new ExternalServiceException("Redirecionamentos demais.");
    }

    private static URI redirectTarget(URI start, URI current, String location) {
        URI target = current.resolve(location);
        String host = target.getHost() == null ? "" : target.getHost().toLowerCase();
        boolean trusted = host.equals(start.getHost()) || host.equals("openlibrary.org") || host.endsWith(".openlibrary.org")
                || host.equals("archive.org") || host.endsWith(".archive.org");
        boolean secure = "https".equals(target.getScheme()) || target.getScheme() != null && target.getScheme().equals(start.getScheme());
        if (!trusted || !secure) {
            throw new ExternalServiceException("Redirecionamento para fora dos servidores confiáveis.");
        }
        return target;
    }
}

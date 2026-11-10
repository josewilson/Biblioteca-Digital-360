package br.com.biblioteca.acervo;

import java.util.List;
import java.util.Map;

/**
 * Sugestão para preencher o formulário "Nova obra". Nada disto é gravado: o usuário revisa e salva pelo cadastro normal.
 * Editora, autor e categoria vêm como {@link Match}: com {@code id} já existe no cadastro; sem {@code id} é só um nome
 * sugerido (ainda não cadastrado). {@code sources} diz de qual provedor veio cada campo, para depuração.
 */
public record IsbnSuggestion(String isbn, boolean alreadyRegistered, Long existingBookId,
                             String title, String edition, Integer publicationYear, String language, String description,
                             String coverUrl, Match publisher, List<Match> authors, List<Match> categories,
                             Map<String, String> sources) {

    public record Match(Long id, String name) {
    }

    /** O ISBN já está no acervo: não consulta ninguém e devolve só o aviso. */
    static IsbnSuggestion duplicate(String isbn, Long bookId) {
        return new IsbnSuggestion(isbn, true, bookId, null, null, null, null, null, null, null, List.of(), List.of(), Map.of());
    }
}

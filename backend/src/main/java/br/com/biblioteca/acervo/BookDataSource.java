package br.com.biblioteca.acervo;

import java.util.Optional;

import br.com.biblioteca.shared.error.ExternalServiceException;

/** Um provedor público de dados de livros por ISBN (BrasilAPI, Open Library). */
interface BookDataSource {

    String name();

    /**
     * @param isbn13 ISBN já normalizado
     * @return os dados, ou vazio se o provedor não conhece o livro
     * @throws ExternalServiceException se o provedor está fora do ar, lento ou respondeu lixo
     */
    Optional<BookData> find(String isbn13);
}

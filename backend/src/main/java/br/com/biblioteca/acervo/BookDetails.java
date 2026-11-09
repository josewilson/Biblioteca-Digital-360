package br.com.biblioteca.acervo;

import java.util.Set;

/** Dados descritivos comuns ao cadastro e à edição de uma obra. */
interface BookDetails {

    String title();

    String isbn();

    String edition();

    Integer publicationYear();

    String language();

    String description();

    String coverUrl();

    Long publisherId();

    Set<Long> authorIds();

    Set<Long> categoryIds();
}

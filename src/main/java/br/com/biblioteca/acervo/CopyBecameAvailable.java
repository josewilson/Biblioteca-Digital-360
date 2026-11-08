package br.com.biblioteca.acervo;

/** Publicado (dentro da transação) quando um exemplar emprestável passa a estar disponível sem ser por devolução. */
public record CopyBecameAvailable(Long bookId) {
}

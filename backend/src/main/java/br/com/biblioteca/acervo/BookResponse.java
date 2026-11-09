package br.com.biblioteca.acervo;

import java.util.Comparator;
import java.util.List;

public record BookResponse(Long id, String title, String isbn, String edition, Short publicationYear,
                           String language, String description, String coverUrl, Long coverVersion, Long publisherId, String publisher,
                           List<NameResponse> authors, List<NameResponse> categories,
                           List<CopyResponse> copies) {

    static BookResponse from(Book b) {
        return new BookResponse(b.getId(), b.getTitle(), b.getIsbn(), b.getEdition(), b.getPublicationYear(),
                b.getLanguage(), b.getDescription(), b.getCoverUrl(), b.getCoverVersion(),
                b.getPublisher() == null ? null : b.getPublisher().getId(),
                b.getPublisher() == null ? null : b.getPublisher().getName(),
                b.getAuthors().stream().map(a -> new NameResponse(a.getId(), a.getName()))
                        .sorted(Comparator.comparing(NameResponse::name)).toList(),
                b.getCategories().stream().map(c -> new NameResponse(c.getId(), c.getName()))
                        .sorted(Comparator.comparing(NameResponse::name)).toList(),
                b.getCopies().stream().map(CopyResponse::from).toList());
    }
}

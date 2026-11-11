package br.com.biblioteca.acervo;

import java.util.Comparator;
import java.util.List;

/** Linha da listagem de exemplares: o exemplar e o essencial da obra a que pertence. */
public record CopyListItem(Long id, String barcode, CopyKind kind, CopyStatus status, CopyCondition condition,
                           String location, Long bookId, String title, String isbn, String publisher,
                           List<String> authors, List<String> categories) {

    static CopyListItem from(BookCopy c) {
        Book b = c.getBook();
        return new CopyListItem(c.getId(), c.getBarcode(), c.getKind(), c.getStatus(), c.getCondition(), c.getLocation(),
                b.getId(), b.getTitle(), b.getIsbn(), b.getPublisher() == null ? null : b.getPublisher().getName(),
                b.getAuthors().stream().map(Author::getName).sorted(Comparator.naturalOrder()).toList(),
                b.getCategories().stream().map(Category::getName).sorted(Comparator.naturalOrder()).toList());
    }
}

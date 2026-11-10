package br.com.biblioteca.acervo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    /** União em vez de OR: cada ramo usa o próprio índice (trigram em título e autor; único em ISBN e código). */
    String MATCHING_IDS = """
            select x.id from Book x
              where function('f_unaccent' as String, lower(x.title)) like function('f_unaccent' as String, lower(:pattern)) escape '\\'
            union select y.id from Book y where y.isbn = :exact
            union select z.id from Book z join z.authors a
              where function('f_unaccent' as String, lower(a.name)) like function('f_unaccent' as String, lower(:pattern)) escape '\\'
            union select c.book.id from BookCopy c where c.barcode = :exact
            """;

    /** Ids das obras cujo ISBN (ignorando hífen e espaço, como foi digitado) é um dos informados. */
    @Query("select b.id from Book b where replace(replace(b.isbn, '-', ''), ' ', '') in :isbns order by b.id")
    java.util.List<Long> findIdsByIsbnIn(@Param("isbns") java.util.Collection<String> isbns);

    boolean existsByIsbn(String isbn);

    boolean existsByIsbnAndIdNot(String isbn, Long id);

    @EntityGraph(attributePaths = "publisher")
    @Query(value = "select b from Book b where b.id in (" + MATCHING_IDS + ")",
           countQuery = "select count(b) from Book b where b.id in (" + MATCHING_IDS + ")")
    Page<Book> search(@Param("pattern") String pattern, @Param("exact") String exact, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "publisher")
    Page<Book> findAll(Pageable pageable);
}

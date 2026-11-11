package br.com.biblioteca.acervo;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    /**
     * Listagem de exemplares. {@code pattern} é sempre um LIKE (use "%" para "tudo"); os demais filtros nulos não filtram.
     * Texto casa com título, autor, editora, categoria, ISBN (sem hífen), código e localização, sem acento.
     */
    @Query(value = """
            select c from BookCopy c join fetch c.book b left join fetch b.publisher p
            where (lower(function('f_unaccent' as String, b.title)) like lower(function('f_unaccent' as String, :pattern)) escape '\\'
                or lower(c.barcode) like lower(:pattern) escape '\\'
                or replace(replace(b.isbn, '-', ''), ' ', '') like :isbnPattern escape '\\'
                or lower(function('f_unaccent' as String, coalesce(c.location, ''))) like lower(function('f_unaccent' as String, :pattern)) escape '\\'
                or lower(function('f_unaccent' as String, coalesce(p.name, ''))) like lower(function('f_unaccent' as String, :pattern)) escape '\\'
                or exists (select 1 from b.authors a
                           where lower(function('f_unaccent' as String, a.name)) like lower(function('f_unaccent' as String, :pattern)) escape '\\')
                or exists (select 1 from b.categories k
                           where lower(function('f_unaccent' as String, k.name)) like lower(function('f_unaccent' as String, :pattern)) escape '\\'))
              and (:status is null or c.status = :status)
              and (:kind is null or c.kind = :kind)
              and (:condition is null or c.condition = :condition)
              and (:categoryId is null or exists (select 1 from b.categories k2 where k2.id = :categoryId))
            order by lower(function('f_unaccent' as String, b.title)), c.barcode
            """,
           countQuery = """
            select count(c) from BookCopy c join c.book b left join b.publisher p
            where (lower(function('f_unaccent' as String, b.title)) like lower(function('f_unaccent' as String, :pattern)) escape '\\'
                or lower(c.barcode) like lower(:pattern) escape '\\'
                or replace(replace(b.isbn, '-', ''), ' ', '') like :isbnPattern escape '\\'
                or lower(function('f_unaccent' as String, coalesce(c.location, ''))) like lower(function('f_unaccent' as String, :pattern)) escape '\\'
                or lower(function('f_unaccent' as String, coalesce(p.name, ''))) like lower(function('f_unaccent' as String, :pattern)) escape '\\'
                or exists (select 1 from b.authors a
                           where lower(function('f_unaccent' as String, a.name)) like lower(function('f_unaccent' as String, :pattern)) escape '\\')
                or exists (select 1 from b.categories k
                           where lower(function('f_unaccent' as String, k.name)) like lower(function('f_unaccent' as String, :pattern)) escape '\\'))
              and (:status is null or c.status = :status)
              and (:kind is null or c.kind = :kind)
              and (:condition is null or c.condition = :condition)
              and (:categoryId is null or exists (select 1 from b.categories k2 where k2.id = :categoryId))
            """)
    org.springframework.data.domain.Page<BookCopy> search(@Param("pattern") String pattern,
                                                          @Param("isbnPattern") String isbnPattern,
                                                          @Param("status") CopyStatus status,
                                                          @Param("kind") CopyKind kind,
                                                          @Param("condition") CopyCondition condition,
                                                          @Param("categoryId") Long categoryId,
                                                          org.springframework.data.domain.Pageable pageable);

    boolean existsByBarcodeIn(Collection<String> barcodes);

    boolean existsByBarcodeAndIdNot(String barcode, Long id);

    boolean existsByBookIdAndKindAndStatus(Long bookId, CopyKind kind, CopyStatus status);

    /** Trava a linha do exemplar até o fim da transação: dois empréstimos simultâneos não passam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.id = :id")
    Optional<BookCopy> findForUpdateById(@Param("id") Long id);

    /** Primeiro exemplar do tipo/situação informados, já travado (usado para guardar exemplar para a fila). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BookCopy> findFirstByBookIdAndKindAndStatusOrderByIdAsc(Long bookId, CopyKind kind, CopyStatus status);
}

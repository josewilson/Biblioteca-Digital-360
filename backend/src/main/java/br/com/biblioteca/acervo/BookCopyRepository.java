package br.com.biblioteca.acervo;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    boolean existsByBarcodeIn(Collection<String> barcodes);

    boolean existsByBookIdAndKindAndStatus(Long bookId, CopyKind kind, CopyStatus status);

    /** Trava a linha do exemplar até o fim da transação: dois empréstimos simultâneos não passam juntos. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.id = :id")
    Optional<BookCopy> findForUpdateById(@Param("id") Long id);

    /** Primeiro exemplar do tipo/situação informados, já travado (usado para guardar exemplar para a fila). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BookCopy> findFirstByBookIdAndKindAndStatusOrderByIdAsc(Long bookId, CopyKind kind, CopyStatus status);
}

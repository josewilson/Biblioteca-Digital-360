package br.com.biblioteca.loan;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, Collection<ReservationStatus> statuses);

    boolean existsByBookIdAndStatus(Long bookId, ReservationStatus status);

    /** Próxima reserva da fila (a mais antiga), já travada. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findFirstByBookIdAndStatusOrderByIdAsc(Long bookId, ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Reservation> findByCopyIdAndStatus(Long copyId, ReservationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :id")
    Optional<Reservation> findForUpdateById(@Param("id") Long id);

    /** Só o id do exemplar guardado (sem carregar a reserva), para travar o exemplar ANTES da reserva. */
    @Query("select r.copy.id from Reservation r where r.id = :id")
    Optional<Long> findCopyIdById(@Param("id") Long id);

    @Query("select r.id from Reservation r where r.status = br.com.biblioteca.loan.ReservationStatus.DISPONIVEL and r.expiresAt < :now")
    List<Long> findExpiredIds(@Param("now") Instant now);

    @Query("select count(r) from Reservation r where r.book.id = :bookId and r.status = br.com.biblioteca.loan.ReservationStatus.NA_FILA and r.id < :id")
    long countAhead(@Param("bookId") Long bookId, @Param("id") Long id);

    /** Quantos estão na frente de cada reserva da lista, numa única consulta (evita uma contagem por linha). */
    @Query("select r.id, (select count(x) from Reservation x where x.book.id = r.book.id and x.status = br.com.biblioteca.loan.ReservationStatus.NA_FILA and x.id < r.id) from Reservation r where r.id in :ids")
    List<Object[]> countAheadFor(@Param("ids") Collection<Long> ids);

    @EntityGraph(attributePaths = "book")
    Page<Reservation> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "book")
    Page<Reservation> findByUserIdAndStatus(Long userId, ReservationStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "book")
    Page<Reservation> findByStatus(ReservationStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "book")
    Page<Reservation> findAll(Pageable pageable);
}

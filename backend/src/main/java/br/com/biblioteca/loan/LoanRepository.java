package br.com.biblioteca.loan;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface LoanRepository extends JpaRepository<Loan, Long> {

    long countByUserIdAndReturnedAtIsNull(Long userId);

    boolean existsByUserIdAndReturnedAtIsNullAndDueAtBefore(Long userId, Instant now);

    /** Trava o empréstimo: devolução e renovação simultâneas do mesmo empréstimo são serializadas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.id = :id")
    Optional<Loan> findForUpdateById(@Param("id") Long id);

    // As listas mostram obra e exemplar: busca junto (@EntityGraph) para não fazer 2 consultas por linha.
    @EntityGraph(attributePaths = {"copy", "copy.book"})
    Page<Loan> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"copy", "copy.book"})
    Page<Loan> findByUserIdAndReturnedAtIsNull(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"copy", "copy.book"})
    Page<Loan> findByReturnedAtIsNull(Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"copy", "copy.book"})
    Page<Loan> findAll(Pageable pageable);
}

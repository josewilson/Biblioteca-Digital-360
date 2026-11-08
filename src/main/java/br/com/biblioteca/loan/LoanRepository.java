package br.com.biblioteca.loan;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.domain.Page;
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

    Page<Loan> findByUserId(Long userId, Pageable pageable);

    Page<Loan> findByUserIdAndReturnedAtIsNull(Long userId, Pageable pageable);

    Page<Loan> findByReturnedAtIsNull(Pageable pageable);
}

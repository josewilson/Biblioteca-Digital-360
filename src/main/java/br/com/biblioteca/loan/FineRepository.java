package br.com.biblioteca.loan;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

interface FineRepository extends JpaRepository<Fine, Long> {

    @Query("select count(f) > 0 from Fine f where f.status = br.com.biblioteca.loan.FineStatus.ABERTA and f.loan.userId = :userId")
    boolean existsOpenByUserId(@Param("userId") Long userId);

    /** Trava a multa: dois pagamentos/isenções simultâneos da mesma multa são serializados. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Fine f where f.id = :id")
    Optional<Fine> findForUpdateById(@Param("id") Long id);

    @EntityGraph(attributePaths = "loan")
    Page<Fine> findByLoanUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "loan")
    Page<Fine> findByLoanUserIdAndStatus(Long userId, FineStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "loan")
    Page<Fine> findByStatus(FineStatus status, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "loan")
    Page<Fine> findAll(Pageable pageable);
}

package br.com.biblioteca.loan;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoanRenewalRepository extends JpaRepository<LoanRenewal, Long> {

    @Query("select count(r) from LoanRenewal r where r.loan.id = :loanId")
    long countByLoanId(@Param("loanId") Long loanId);
}

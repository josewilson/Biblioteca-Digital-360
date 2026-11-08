package br.com.biblioteca.loan;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface LoanPolicyRepository extends JpaRepository<LoanPolicy, Long> {

    @Query("select p from LoanPolicy p where p.role.code in :codes")
    List<LoanPolicy> findByRoleCodes(@Param("codes") Collection<String> codes);
}

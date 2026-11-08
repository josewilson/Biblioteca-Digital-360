package br.com.biblioteca.loan;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface FineRateRepository extends JpaRepository<FineRate, Long> {

    @Query("select r from FineRate r where r.role.code in :codes")
    List<FineRate> findByRoleCodes(@Param("codes") Collection<String> codes);
}

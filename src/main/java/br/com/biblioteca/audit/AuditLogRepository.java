package br.com.biblioteca.audit;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query("select count(a) from AuditLog a where a.action = 'LOGIN_FAILED' and a.details = :email and a.createdAt > :since")
    long countFailedLogins(@Param("email") String email, @Param("since") Instant since);
}

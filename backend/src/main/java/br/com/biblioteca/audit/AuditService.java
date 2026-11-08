package br.com.biblioteca.audit;

import java.time.Instant;

import org.springframework.stereotype.Service;

/** Registra operações relevantes em audit_logs. */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void record(Long userId, String action, String entity, Long entityId, String details, String ip) {
        repository.save(new AuditLog(userId, action, entity, entityId, details, ip));
    }

    /** Quantas tentativas de login falharam para este e-mail desde o instante dado (base do bloqueio por força bruta). */
    public long failedLoginsSince(String email, Instant since) {
        return repository.countFailedLogins(email, since);
    }
}

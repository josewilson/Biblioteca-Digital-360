package br.com.biblioteca.audit;

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
}

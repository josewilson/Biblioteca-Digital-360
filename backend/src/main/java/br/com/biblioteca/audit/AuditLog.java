package br.com.biblioteca.audit;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String action;
    private String entity;
    private Long entityId;
    private String details;
    private String ipAddress;
    private Instant createdAt = Instant.now();

    protected AuditLog() {
    }

    public AuditLog(Long userId, String action, String entity, Long entityId, String details, String ipAddress) {
        this.userId = userId;
        this.action = action;
        this.entity = entity;
        this.entityId = entityId;
        this.details = details;
        this.ipAddress = ipAddress;
    }
}

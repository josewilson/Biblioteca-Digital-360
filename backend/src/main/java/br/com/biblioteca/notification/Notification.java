package br.com.biblioteca.notification;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "notifications")
class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String type;
    private String message;
    private String channel = "EMAIL";
    private Instant sentAt;                 // nulo até o envio real (Fase 6)
    private Instant createdAt = Instant.now();

    protected Notification() {
    }

    Notification(Long userId, String type, String message) {
        this.userId = userId;
        this.type = type;
        this.message = message;
    }
}

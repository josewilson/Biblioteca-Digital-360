package br.com.biblioteca.notification;

import org.springframework.stereotype.Service;

/**
 * Registra o aviso ao leitor (tabela notifications) na mesma transação da operação.
 * O envio por e-mail/outro canal é feito depois, de forma assíncrona (Fase 6): nunca dentro da transação.
 */
@Service
public class NotificationService {

    private final NotificationRepository repository;

    NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    public void record(Long userId, String type, String message) {
        repository.save(new Notification(userId, type, message));
    }
}

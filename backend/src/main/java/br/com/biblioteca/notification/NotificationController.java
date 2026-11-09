package br.com.biblioteca.notification;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.security.Access;
import br.com.biblioteca.shared.error.BusinessRuleException;
import jakarta.servlet.http.HttpServletRequest;

/** Confere a configuração de e-mail de verdade: manda um aviso de teste para o e-mail de quem pediu. */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize(Access.ADMIN)
public class NotificationController {

    private final NotificationChannel channel;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final String channelName;

    NotificationController(NotificationChannel channel, JdbcTemplate jdbc, AuditService audit,
                           @Value("${app.notifications.channel:log}") String channelName) {
        this.channel = channel;
        this.jdbc = jdbc;
        this.audit = audit;
        this.channelName = channelName;
    }

    /** Com o canal {@code log} nada sai de verdade; a resposta diz qual canal está ligado. */
    @PostMapping("/test")
    public Map<String, String> test(Authentication auth, HttpServletRequest http) {
        Long callerId = Long.valueOf(auth.getName());
        String email = jdbc.queryForObject("select email from users where id = ?", String.class, callerId);
        try {
            channel.send(email, "Teste de e-mail — Biblioteca Digital 360",
                    "Se você recebeu esta mensagem, o envio de avisos está funcionando.");
        } catch (RuntimeException e) {
            audit.record(callerId, "EMAIL_TEST_FAILED", "notification", null, null, http.getRemoteAddr());
            throw new BusinessRuleException("Não foi possível enviar: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        audit.record(callerId, "EMAIL_TEST_SENT", "notification", null, null, http.getRemoteAddr());
        return Map.of("channel", channelName, "to", email);
    }
}

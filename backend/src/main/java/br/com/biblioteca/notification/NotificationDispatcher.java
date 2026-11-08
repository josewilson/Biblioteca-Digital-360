package br.com.biblioteca.notification;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Entrega os avisos pendentes (tabela notifications) pelo canal configurado, fora de qualquer transação de negócio.
 * Falha de entrega não perde o aviso: conta a tentativa e tenta de novo, até {@value #MAX_ATTEMPTS} vezes.
 */
@Component
public class NotificationDispatcher {

    static final int MAX_ATTEMPTS = 5;
    private static final int BATCH = 50;

    private static final Map<String, String> SUBJECTS = Map.of(
            "LOAN_CONFIRMED", "Empréstimo confirmado",
            "LOAN_DUE_SOON", "Sua devolução se aproxima",
            "LOAN_OVERDUE", "Livro em atraso",
            "RESERVATION_AVAILABLE", "Sua reserva está disponível",
            "RESERVATION_EXPIRED", "Sua reserva expirou",
            "FINE_REGISTERED", "Multa registrada",
            "PAYMENT_CONFIRMED", "Pagamento confirmado");

    private record Pending(long id, String email, String type, String message) {
    }

    private final JdbcTemplate jdbc;
    private final NotificationChannel channel;

    NotificationDispatcher(JdbcTemplate jdbc, NotificationChannel channel) {
        this.jdbc = jdbc;
        this.channel = channel;
    }

    /** @return quantos avisos foram entregues nesta rodada */
    // ponytail: uma instância só (fixedDelay serializa). Com várias instâncias, trocar a busca por FOR UPDATE SKIP LOCKED.
    @Scheduled(initialDelayString = "PT30S", fixedDelayString = "PT1M")
    public int dispatchPending() {
        List<Pending> pending = jdbc.query("""
                select n.id, u.email, n.type, n.message
                from notifications n join users u on u.id = n.user_id
                where n.sent_at is null and n.attempts < ?
                order by n.id limit ?""",
                (rs, i) -> new Pending(rs.getLong("id"), rs.getString("email"), rs.getString("type"),
                        rs.getString("message")),
                MAX_ATTEMPTS, BATCH);

        int delivered = 0;
        for (Pending p : pending) {
            try {
                channel.send(p.email(), SUBJECTS.getOrDefault(p.type(), "Biblioteca Digital 360"), p.message());
                jdbc.update("update notifications set sent_at = now() where id = ? and sent_at is null", p.id());
                delivered++;
            } catch (RuntimeException e) {
                jdbc.update("update notifications set attempts = attempts + 1, last_error = left(?, 300) where id = ?",
                        e.getClass().getSimpleName() + ": " + e.getMessage(), p.id());
            }
        }
        return delivered;
    }
}

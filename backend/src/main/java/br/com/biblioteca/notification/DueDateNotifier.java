package br.com.biblioteca.notification;

import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Todo dia de manhã: avisa quem tem devolução amanhã e quem está com livro atrasado.
 * Cada empréstimo recebe cada aviso uma única vez (dedup_key UNIQUE), mesmo que a rotina rode de novo.
 */
@Component
public class DueDateNotifier {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    DueDateNotifier(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 8 * * *", zone = "${app.zone:America/Sao_Paulo}")
    public void run() {
        notifyDueTomorrow();
        notifyOverdue();
    }

    /** @return quantos avisos novos foram criados */
    public int notifyDueTomorrow() {
        String zone = clock.getZone().getId();
        return jdbc.update("""
                insert into notifications (user_id, type, message, dedup_key)
                select l.user_id, 'LOAN_DUE_SOON',
                       'O livro "' || b.title || '" deve ser devolvido amanhã (' || to_char(l.due_at at time zone ?, 'DD/MM/YYYY') || ').',
                       'LOAN_DUE_SOON:' || l.id
                from loans l join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
                where l.returned_at is null and (l.due_at at time zone ?)::date = ?
                on conflict (dedup_key) do nothing""",
                zone, zone, Date.valueOf(LocalDate.now(clock).plusDays(1)));
    }

    /** @return quantos avisos novos foram criados */
    public int notifyOverdue() {
        String zone = clock.getZone().getId();
        return jdbc.update("""
                insert into notifications (user_id, type, message, dedup_key)
                select l.user_id, 'LOAN_OVERDUE',
                       'O livro "' || b.title || '" está atrasado desde ' || to_char(l.due_at at time zone ?, 'DD/MM/YYYY')
                       || '. Devolva o quanto antes: há multa por dia de atraso.',
                       'LOAN_OVERDUE:' || l.id
                from loans l join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
                where l.returned_at is null and (l.due_at at time zone ?)::date < ?
                on conflict (dedup_key) do nothing""",
                zone, zone, Date.valueOf(LocalDate.now(clock)));
    }
}

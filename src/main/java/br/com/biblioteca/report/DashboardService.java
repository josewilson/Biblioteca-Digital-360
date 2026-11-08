package br.com.biblioteca.report;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.report.DashboardResponse.DailyMovement;
import br.com.biblioteca.report.DashboardResponse.RankItem;

/**
 * Indicadores do painel. Consultas só de leitura, em SQL, porque são agregações (contagens e somas) que
 * não pertencem a nenhuma entidade. Uma transação de leitura garante números coerentes entre si.
 */
@Service
public class DashboardService {

    private static final int RANKING_SIZE = 5;
    private static final int MOVEMENT_DAYS = 30;

    private final JdbcTemplate jdbc;
    private final Clock clock;

    DashboardService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        Timestamp now = Timestamp.from(clock.instant());
        String zone = clock.getZone().getId();
        LocalDate today = LocalDate.now(clock);

        return jdbc.queryForObject("""
                select
                  (select count(*) from books) as total_books,
                  (select count(*) from book_copies where status not in ('PERDIDO', 'BAIXADO')) as total_copies,
                  (select count(*) from users where active and deleted_at is null) as active_users,
                  (select count(*) from loans where returned_at is null) as active_loans,
                  (select count(*) from loans where returned_at is null and due_at < ?) as overdue_loans,
                  (select count(*) from reservations where status in ('NA_FILA', 'DISPONIVEL')) as pending_reservations,
                  (select count(*) from fines where status = 'ABERTA') as open_fines_count,
                  (select coalesce(sum(amount), 0) from fines where status = 'ABERTA') as open_fines_amount""",
                (rs, i) -> new DashboardResponse(rs.getLong("total_books"), rs.getLong("total_copies"),
                        rs.getLong("active_users"), rs.getLong("active_loans"), rs.getLong("overdue_loans"),
                        rs.getLong("pending_reservations"), rs.getLong("open_fines_count"),
                        rs.getBigDecimal("open_fines_amount"), topBooks(), topCategories(), movements(zone, today)),
                now);
    }

    private List<RankItem> topBooks() {
        return jdbc.query("""
                select b.title as name, count(*) as total
                from loans l join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
                group by b.id, b.title order by total desc, b.title limit ?""",
                (rs, i) -> new RankItem(rs.getString("name"), rs.getLong("total")), RANKING_SIZE);
    }

    private List<RankItem> topCategories() {
        return jdbc.query("""
                select cat.name as name, count(*) as total
                from loans l join book_copies c on c.id = l.copy_id
                     join book_categories bc on bc.book_id = c.book_id join categories cat on cat.id = bc.category_id
                group by cat.id, cat.name order by total desc, cat.name limit ?""",
                (rs, i) -> new RankItem(rs.getString("name"), rs.getLong("total")), RANKING_SIZE);
    }

    private List<DailyMovement> movements(String zone, LocalDate today) {
        return jdbc.query("""
                select d::date as day,
                       (select count(*) from loans where (loaned_at at time zone ?)::date = d::date) as loans,
                       (select count(*) from loans where (returned_at at time zone ?)::date = d::date) as returns
                from generate_series(cast(? as date) - ?, cast(? as date), interval '1 day') as d
                order by day""",
                (rs, i) -> new DailyMovement(rs.getDate("day").toLocalDate(), rs.getLong("loans"), rs.getLong("returns")),
                zone, zone, Date.valueOf(today), MOVEMENT_DAYS - 1, Date.valueOf(today));
    }
}

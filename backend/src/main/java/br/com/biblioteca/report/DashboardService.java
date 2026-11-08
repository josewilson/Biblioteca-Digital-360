package br.com.biblioteca.report;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.report.DashboardResponse.DailyMovement;
import br.com.biblioteca.report.DashboardResponse.RankItem;

/**
 * Indicadores do painel. Consultas só de leitura, em SQL, porque são agregações (contagens e somas) que
 * não pertencem a nenhuma entidade. Uma transação de leitura garante números coerentes entre si.
 *
 * Desempenho (medido com 205 mil empréstimos): o movimento diário usa UMA passagem com filtro por intervalo
 * (índice em loaned_at/returned_at) em vez de duas subconsultas por dia, que custavam ~5 s; os dois rankings
 * compartilham a mesma agregação por obra.
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
        LocalDate today = LocalDate.now(clock);
        List<RankItem> topBooks = new ArrayList<>();
        List<RankItem> topCategories = new ArrayList<>();
        loadRankings(topBooks, topCategories);
        List<DailyMovement> movements = movements(today);

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
                        rs.getBigDecimal("open_fines_amount"), topBooks, topCategories, movements),
                now);
    }

    /** Obras e categorias mais emprestadas, numa só consulta (a agregação por obra é feita uma vez). */
    private void loadRankings(List<RankItem> topBooks, List<RankItem> topCategories) {
        jdbc.query("""
                with per_book as (
                    select c.book_id, count(*) as total
                    from loans l join book_copies c on c.id = l.copy_id
                    group by c.book_id)
                (select 'BOOK' as kind, b.title as name, p.total
                   from per_book p join books b on b.id = p.book_id
                  order by p.total desc, b.title limit ?)
                union all
                (select 'CATEGORY', cat.name, sum(p.total)::bigint
                   from per_book p join book_categories bc on bc.book_id = p.book_id
                        join categories cat on cat.id = bc.category_id
                  group by cat.id, cat.name order by 3 desc, cat.name limit ?)""",
                rs -> {
                    RankItem item = new RankItem(rs.getString("name"), rs.getLong("total"));
                    (rs.getString("kind").equals("BOOK") ? topBooks : topCategories).add(item);
                },
                RANKING_SIZE, RANKING_SIZE);
    }

    private List<DailyMovement> movements(LocalDate today) {
        String zone = clock.getZone().getId();
        Timestamp from = Timestamp.from(today.minusDays(MOVEMENT_DAYS - 1L).atStartOfDay(clock.getZone()).toInstant());
        Timestamp to = Timestamp.from(today.plusDays(1).atStartOfDay(clock.getZone()).toInstant());
        return jdbc.query("""
                select d.day, coalesce(l.n, 0) as loans, coalesce(r.n, 0) as returns
                from (select gs::date as day
                        from generate_series(cast(? as date) - ?, cast(? as date), interval '1 day') gs) d
                left join (select (loaned_at at time zone ?)::date as day, count(*) as n
                             from loans where loaned_at >= ? and loaned_at < ? group by 1) l on l.day = d.day
                left join (select (returned_at at time zone ?)::date as day, count(*) as n
                             from loans where returned_at >= ? and returned_at < ? group by 1) r on r.day = d.day
                order by d.day""",
                (rs, i) -> new DailyMovement(rs.getDate("day").toLocalDate(), rs.getLong("loans"), rs.getLong("returns")),
                Date.valueOf(today), MOVEMENT_DAYS - 1, Date.valueOf(today),
                zone, from, to,
                zone, from, to);
    }
}

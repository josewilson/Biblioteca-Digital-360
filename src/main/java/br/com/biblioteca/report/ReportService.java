package br.com.biblioteca.report;

import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.shared.error.BusinessRuleException;

@Service
public class ReportService {

    static final int MAX_ROWS = 10_000;
    static final int MAX_DAYS = 366;
    private static final int DEFAULT_DAYS = 30;

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    ReportService(NamedParameterJdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /**
     * @param from primeiro dia do período (padrão: 29 dias antes de {@code to})
     * @param to   último dia do período, inclusive (padrão: hoje)
     */
    @Transactional(readOnly = true)
    public ReportTable run(String slug, LocalDate from, LocalDate to) {
        ReportType type = ReportType.fromSlug(slug);
        LocalDate end = to != null ? to : LocalDate.now(clock);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1L);
        if (start.isAfter(end)) {
            throw new BusinessRuleException("A data inicial não pode ser depois da final.");
        }
        if (start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new BusinessRuleException("O período máximo é de " + MAX_DAYS + " dias.");
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", Timestamp.from(start.atStartOfDay(clock.getZone()).toInstant()))
                .addValue("to", Timestamp.from(end.plusDays(1).atStartOfDay(clock.getZone()).toInstant()))
                .addValue("now", Timestamp.from(clock.instant()))
                .addValue("zone", clock.getZone().getId())
                .addValue("limit", MAX_ROWS + 1);   // uma a mais, só para saber se passou do limite

        return jdbc.query(type.sql, params, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            while (rs.next() && rows.size() <= MAX_ROWS) {
                List<Object> row = new ArrayList<>();
                for (int i = 1; i <= columns.size(); i++) {
                    Object value = rs.getObject(i);
                    row.add(value instanceof Timestamp ts ? ts.toInstant() : value);
                }
                rows.add(row);
            }
            boolean truncated = rows.size() > MAX_ROWS;
            return new ReportTable(slug, columns, truncated ? rows.subList(0, MAX_ROWS) : rows, truncated);
        });
    }

    public String csv(ReportTable table) {
        return ReportCsv.render(table);
    }
}

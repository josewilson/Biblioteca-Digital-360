package br.com.biblioteca.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Indicadores gerenciais. Valores em reais. */
public record DashboardResponse(long totalBooks, long totalCopies, long activeUsers, long activeLoans,
                                long overdueLoans, long pendingReservations, long openFinesCount,
                                BigDecimal openFinesAmount, List<RankItem> topBooks, List<RankItem> topCategories,
                                List<DailyMovement> movements) {

    /** Item de ranking (obra ou categoria) com o total de empréstimos. */
    public record RankItem(String name, long total) {
    }

    /** Empréstimos e devoluções de um dia (últimos 30 dias, para gráfico). */
    public record DailyMovement(LocalDate day, long loans, long returns) {
    }
}

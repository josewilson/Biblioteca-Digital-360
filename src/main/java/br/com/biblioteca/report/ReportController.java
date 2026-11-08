package br.com.biblioteca.report;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.biblioteca.security.Access;
import br.com.biblioteca.shared.error.BusinessRuleException;

/** Painel e relatórios: só a equipe da biblioteca. */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize(Access.STAFF)
public class ReportController {

    private final DashboardService dashboard;
    private final ReportService reports;

    public ReportController(DashboardService dashboard, ReportService reports) {
        this.dashboard = dashboard;
        this.reports = reports;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return dashboard.dashboard();
    }

    /**
     * Relatórios: loans, returns, overdue, fines, payments, users, top-books, availability.
     * {@code format=csv} baixa o arquivo; o padrão é JSON.
     */
    @GetMapping("/reports/{slug}")
    public ResponseEntity<?> report(@PathVariable String slug,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                    @RequestParam(defaultValue = "json") String format) {
        if (!format.equals("json") && !format.equals("csv")) {
            throw new BusinessRuleException("Formato inválido: use json ou csv.");
        }
        ReportTable table = reports.run(slug, from, to);
        if (format.equals("json")) {
            return ResponseEntity.ok(table);
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("relatorio-" + slug + ".csv").build().toString())
                .body(reports.csv(table));
    }
}

package br.com.biblioteca.report;

import java.util.List;

/** Resultado tabular de um relatório (serve igual para JSON e CSV). {@code truncated}: passou do limite de linhas. */
public record ReportTable(String report, List<String> columns, List<List<Object>> rows, boolean truncated) {
}

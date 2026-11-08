package br.com.biblioteca.report;

import java.util.List;

/** Gera CSV (UTF-8 com BOM, para o Excel abrir os acentos certos). */
final class ReportCsv {

    private ReportCsv() {
    }

    static String render(ReportTable table) {
        StringBuilder out = new StringBuilder("﻿");
        appendRow(out, table.columns());
        for (List<Object> row : table.rows()) {
            appendRow(out, row);
        }
        return out.toString();
    }

    private static void appendRow(StringBuilder out, List<?> cells) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            out.append(cell(cells.get(i)));
        }
        out.append("\r\n");
    }

    static String cell(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        // Texto vindo do usuário que começa com = + - @ vira fórmula no Excel/Sheets: neutraliza (só em textos, não em números).
        if (value instanceof CharSequence && !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) {
            text = '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }
}

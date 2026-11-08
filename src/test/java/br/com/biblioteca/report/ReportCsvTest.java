package br.com.biblioteca.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReportCsvTest {

    private ReportTable table(Object... row) {
        return new ReportTable("x", List.of("a", "b"), List.of(Arrays.asList(row)), false);
    }

    @Test
    void comecaComBomECabecalhoELinhasTerminamEmCrlf() {
        String csv = ReportCsv.render(table("Dom Casmurro", 3L));

        assertEquals("﻿a,b\r\nDom Casmurro,3\r\n", csv);
    }

    @Test
    void textoComVirgulaAspasOuQuebraDeLinhaVaiEntreAspas() {
        assertEquals("\"Casmurro, Dom\"", ReportCsv.cell("Casmurro, Dom"));
        assertEquals("\"disse \"\"oi\"\"\"", ReportCsv.cell("disse \"oi\""));
        assertEquals("\"linha1\nlinha2\"", ReportCsv.cell("linha1\nlinha2"));
    }

    @Test
    void textoQueViraFormulaNoExcelEhNeutralizado() {
        assertEquals("'=HYPERLINK(\"http://x\")".replace("\"", "\"\""), ReportCsv.cell("=HYPERLINK(\"http://x\")").replaceAll("^\"|\"$", ""));
        assertEquals("'+55 11 99999", ReportCsv.cell("+55 11 99999"));
        assertEquals("'@usuario", ReportCsv.cell("@usuario"));
        assertEquals("'-1+1", ReportCsv.cell("-1+1"));
    }

    @Test
    void numeroNegativoNaoEhAlteradoPoisNaoEhTexto() {
        assertEquals("-5", ReportCsv.cell(-5L));
        assertEquals("3.00", ReportCsv.cell(new BigDecimal("3.00")));
    }

    @Test
    void nuloViraCelulaVazia() {
        assertEquals("a,b\r\n,x\r\n", ReportCsv.render(table(null, "x")).substring(1));
        assertTrue(ReportCsv.render(table(null, null)).endsWith(",\r\n"));
    }
}

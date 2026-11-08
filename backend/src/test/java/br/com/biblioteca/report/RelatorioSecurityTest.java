package br.com.biblioteca.report;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import br.com.biblioteca.security.SecurityConfig;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=segredo-de-teste-com-mais-de-32-caracteres")
class RelatorioSecurityTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    DashboardService dashboard;

    @MockitoBean
    ReportService reports;

    private static RequestPostProcessor as(String role) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Test
    void leitorNaoVePainelNemRelatorios() throws Exception {
        mvc.perform(get("/api/v1/dashboard").with(as("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/loans").with(as("TEACHER"))).andExpect(status().isForbidden());
    }

    @Test
    void semTokenRecebe401() throws Exception {
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void equipeVePainelERelatorioEmJson() throws Exception {
        when(reports.run(eq("loans"), any(), any()))
                .thenReturn(new ReportTable("loans", List.of("emprestimo"), List.of(List.of(1L)), false));

        mvc.perform(get("/api/v1/dashboard").with(as("ASSISTANT"))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/reports/loans").with(as("LIBRARIAN"))).andExpect(status().isOk());
    }

    @Test
    void csvVemComoAnexoComTipoCorreto() throws Exception {
        ReportTable table = new ReportTable("loans", List.of("emprestimo"), List.of(List.of(1L)), false);
        when(reports.run(eq("loans"), any(), any())).thenReturn(table);
        when(reports.csv(table)).thenReturn("﻿emprestimo\r\n1\r\n");

        mvc.perform(get("/api/v1/reports/loans?format=csv").with(as("LIBRARIAN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"relatorio-loans.csv\""))
                .andExpect(content().string("﻿emprestimo\r\n1\r\n"));
    }

    @Test
    void formatoInvalidoRecebe422() throws Exception {
        mvc.perform(get("/api/v1/reports/loans?format=xml").with(as("LIBRARIAN")))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void relatorioInexistenteRecebe404() throws Exception {
        when(reports.run(eq("segredos"), any(), any())).thenThrow(new ResourceNotFoundException("Relatório inexistente"));
        mvc.perform(get("/api/v1/reports/segredos").with(as("LIBRARIAN"))).andExpect(status().isNotFound());
    }

    @Test
    void dataEmFormatoErradoRecebe400() throws Exception {
        mvc.perform(get("/api/v1/reports/loans?from=ontem").with(as("LIBRARIAN"))).andExpect(status().isBadRequest());
    }
}

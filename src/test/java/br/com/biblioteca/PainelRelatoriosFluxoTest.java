package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Painel e relatórios contra PostgreSQL real. Um único cenário, a partir do banco vazio, para os números serem exatos:
 * 4 usuários, 2 obras (4 exemplares), 2 empréstimos, 1 devolvida com 3 dias de atraso (multa R$ 3,00),
 * 1 ainda atrasado e 1 reserva na fila.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmprestimoFluxoTest.RelogioDeTeste.class)
class PainelRelatoriosFluxoTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.jwt.secret", () -> "segredo-de-teste-com-mais-de-32-caracteres");
        r.add("app.admin.email", () -> "admin@teste.com");
        r.add("app.admin.password", () -> "senha-admin-123");
    }

    @Autowired MockMvc mvc;
    @Autowired MutableClock clock;

    private final ObjectMapper json = new ObjectMapper();
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private String admin;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    private ResultActions send(String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", admin).contentType(JSON).content(body));
    }

    private ResultActions fetch(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", admin));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long user(String name, String role) throws Exception {
        String email = name.toLowerCase() + "@teste.com";
        return read(send("/api/v1/users", """
                {"fullName":"%s","email":"%s","password":"senha-leitor-1","roles":["%s"]}""".formatted(name, email, role))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    /** @return {bookId, circulatingCopyId} */
    private long[] book(String title, Long categoryId) throws Exception {
        String categories = categoryId == null ? "[]" : "[" + categoryId + "]";
        JsonNode b = read(send("/api/v1/books", """
                {"title":"%s","categoryIds":%s,"copies":[{"barcode":"C-%s","kind":"CONSULTA"},{"barcode":"E-%s","kind":"CIRCULANTE"}]}"""
                .formatted(title, categories, title, title)).andExpect(status().isCreated()));
        for (JsonNode c : b.get("copies")) {
            if (c.get("kind").asText().equals("CIRCULANTE")) {
                return new long[] {b.get("id").asLong(), c.get("id").asLong()};
            }
        }
        throw new IllegalStateException();
    }

    @Test
    void painelERelatoriosRefletemOsDadosEmNumerosExatos() throws Exception {
        clock.reset();
        admin = login("admin@teste.com", "senha-admin-123");

        long ana = user("Ana", "STUDENT");
        long bruno = user("Bruno", "TEACHER");
        long carla = user("Carla", "STUDENT");
        long romance = read(send("/api/v1/categories", "{\"name\":\"Romance\"}")).get("id").asLong();
        long[] alfa = book("Alfa", romance);
        long[] beta = book("Beta", null);

        long anaLoan = read(send("/api/v1/loans", "{\"userId\":" + ana + ",\"copyId\":" + alfa[1] + "}")
                .andExpect(status().isCreated())).get("id").asLong();
        send("/api/v1/loans", "{\"userId\":" + bruno + ",\"copyId\":" + beta[1] + "}").andExpect(status().isCreated());
        clock.advanceDays(17);                                                         // prazo padrão: 14 dias
        long fineId = read(send("/api/v1/loans/" + anaLoan + "/return", "").andExpect(status().isOk()))
                .get("fine").get("id").asLong();
        send("/api/v1/reservations", "{\"userId\":" + carla + ",\"bookId\":" + beta[0] + "}").andExpect(status().isCreated());

        // ---------- painel ----------
        LocalDate today = LocalDate.now(clock);
        fetch("/api/v1/dashboard").andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBooks").value(2))
                .andExpect(jsonPath("$.totalCopies").value(4))
                .andExpect(jsonPath("$.activeUsers").value(4))                         // admin + 3 leitores
                .andExpect(jsonPath("$.activeLoans").value(1))
                .andExpect(jsonPath("$.overdueLoans").value(1))
                .andExpect(jsonPath("$.pendingReservations").value(1))
                .andExpect(jsonPath("$.openFinesCount").value(1))
                .andExpect(jsonPath("$.openFinesAmount").value(3.0))
                .andExpect(jsonPath("$.topBooks.length()").value(2))
                .andExpect(jsonPath("$.topBooks[0].name").value("Alfa"))
                .andExpect(jsonPath("$.topBooks[0].total").value(1))
                .andExpect(jsonPath("$.topCategories[0].name").value("Romance"))
                .andExpect(jsonPath("$.topCategories[0].total").value(1))
                .andExpect(jsonPath("$.movements.length()").value(30))
                .andExpect(jsonPath("$.movements[29].day").value(today.toString()))
                .andExpect(jsonPath("$.movements[29].returns").value(1))
                .andExpect(jsonPath("$.movements[12].day").value(today.minusDays(17).toString()))
                .andExpect(jsonPath("$.movements[12].loans").value(2));

        // ---------- relatórios (período padrão: últimos 30 dias) ----------
        JsonNode loans = read(fetch("/api/v1/reports/loans").andExpect(status().isOk()));
        assertEquals("emprestimo", loans.get("columns").get(0).asText());
        assertEquals(2, loans.get("rows").size());
        String situacoes = loans.get("rows").get(0).get(7).asText() + loans.get("rows").get(1).get(7).asText();
        assertTrue(situacoes.contains("ATRASADO") && situacoes.contains("DEVOLVIDO"), situacoes);

        fetch("/api/v1/reports/returns").andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0][6]").value(3));                         // dias de atraso
        fetch("/api/v1/reports/overdue").andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0][1]").value("Bruno"))
                .andExpect(jsonPath("$.rows[0][6]").value(3));
        fetch("/api/v1/reports/fines").andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0][3]").value(3.0))
                .andExpect(jsonPath("$.rows[0][5]").value("ABERTA"));
        fetch("/api/v1/reports/payments").andExpect(jsonPath("$.rows.length()").value(0));
        fetch("/api/v1/reports/top-books").andExpect(jsonPath("$.rows.length()").value(2));
        fetch("/api/v1/reports/availability").andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0][0]").value("Alfa"))
                .andExpect(jsonPath("$.rows[0][1]").value(2))       // exemplares ativos
                .andExpect(jsonPath("$.rows[0][2]").value(1))       // de consulta
                .andExpect(jsonPath("$.rows[0][3]").value(1))       // emprestáveis disponíveis (devolvido)
                .andExpect(jsonPath("$.rows[1][0]").value("Beta"))
                .andExpect(jsonPath("$.rows[1][3]").value(0))
                .andExpect(jsonPath("$.rows[1][4]").value(1));      // emprestado ao Bruno
        JsonNode users = read(fetch("/api/v1/reports/users").andExpect(status().isOk()));
        assertEquals("[\"perfil\",\"ativos\",\"total\"]", users.get("columns").toString());

        // pagar a multa move o painel e preenche o relatório de pagamentos
        send("/api/v1/fines/" + fineId + "/payments", "{\"method\":\"PIX\"}").andExpect(status().isCreated());
        fetch("/api/v1/reports/payments").andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0][3]").value(3.0))
                .andExpect(jsonPath("$.rows[0][4]").value("PIX"));
        fetch("/api/v1/dashboard").andExpect(jsonPath("$.openFinesCount").value(0))
                .andExpect(jsonPath("$.openFinesAmount").value(0));

        // ---------- CSV ----------
        String csv = fetch("/api/v1/reports/loans?format=csv").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("﻿emprestimo,leitor,obra,exemplar,emprestado_em,vencimento,devolvido_em,situacao\r\n"), csv);
        assertTrue(csv.contains("Bruno") && csv.contains("ATRASADO"));

        // ---------- validações e acesso ----------
        fetch("/api/v1/reports/loans?from=" + today + "&to=" + today.minusDays(1)).andExpect(status().isUnprocessableEntity());
        fetch("/api/v1/reports/loans?from=" + today.minusDays(400) + "&to=" + today).andExpect(status().isUnprocessableEntity());
        fetch("/api/v1/reports/segredos").andExpect(status().isNotFound());
        String student = login("ana@teste.com", "senha-leitor-1");
        mvc.perform(get("/api/v1/dashboard").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/reports/users").header("Authorization", student)).andExpect(status().isForbidden());
    }
}

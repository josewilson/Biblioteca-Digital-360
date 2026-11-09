package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Empréstimo, devolução, multa e renovação contra PostgreSQL real, inclusive com requisições simultâneas. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmprestimoFluxoTest.RelogioDeTeste.class)
class EmprestimoFluxoTest {

    @TestConfiguration
    static class RelogioDeTeste {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(ZoneId.of("America/Sao_Paulo"));
        }
    }

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
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();
    private static final AtomicInteger seq = new AtomicInteger();   // estático: o banco é compartilhado entre os testes
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@teste.com\",\"password\":\"senha-admin-123\"}"))
                .andReturn().getResponse().getContentAsString();
        token = "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    // ---------- ajudantes ----------

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long user(String role) throws Exception {
        int n = seq.incrementAndGet();
        return read(postJson("/api/v1/users", """
                {"fullName":"Leitor %d","email":"leitor%d@teste.com","password":"senha-leitor-1","roles":["%s"]}"""
                .formatted(n, n, role)).andExpect(status().isCreated())).get("id").asLong();
    }

    /** Cria uma obra com 1 exemplar de consulta e 1 emprestável; devolve o JSON da obra. */
    private JsonNode book() throws Exception {
        int n = seq.incrementAndGet();
        return read(postJson("/api/v1/books", """
                {"title":"Obra %d","copies":[{"barcode":"C-%d","kind":"CONSULTA"},{"barcode":"E-%d","kind":"CIRCULANTE"}]}"""
                .formatted(n, n, n)).andExpect(status().isCreated()));
    }

    private long circulatingCopy(JsonNode book) {
        for (JsonNode c : book.get("copies")) {
            if (c.get("kind").asText().equals("CIRCULANTE")) {
                return c.get("id").asLong();
            }
        }
        throw new IllegalStateException();
    }

    private long consultaCopy(JsonNode book) {
        for (JsonNode c : book.get("copies")) {
            if (c.get("kind").asText().equals("CONSULTA")) {
                return c.get("id").asLong();
            }
        }
        throw new IllegalStateException();
    }

    private ResultActions lend(long userId, long copyId) throws Exception {
        return postJson("/api/v1/loans", "{\"userId\":" + userId + ",\"copyId\":" + copyId + "}");
    }

    private int lendStatus(long userId, long copyId) throws Exception {
        return lend(userId, copyId).andReturn().getResponse().getStatus();
    }

    private List<Integer> inParallel(List<Callable<Integer>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(tasks)) {
                statuses.add(f.get());
            }
            return statuses;
        } finally {
            pool.shutdown();
        }
    }

    // ---------- cenários ----------

    @Test
    void limiteDeTresConsultaVisitanteERenovacao() throws Exception {
        long ana = user("STUDENT");
        long[] loanIds = new long[3];
        for (int i = 0; i < 3; i++) {
            loanIds[i] = read(lend(ana, circulatingCopy(book())).andExpect(status().isCreated())).get("id").asLong();
        }
        long quarto = circulatingCopy(book());
        lend(ana, quarto).andExpect(status().isUnprocessableEntity());          // limite de 3

        // devolver um libera vaga
        postJson("/api/v1/loans/" + loanIds[0] + "/return", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.fine").doesNotExist());
        lend(ana, quarto).andExpect(status().isCreated());

        // exemplar de consulta nunca sai; exemplar já emprestado não sai de novo; visitante não empresta
        long bob = user("TEACHER");
        JsonNode b = book();
        lend(bob, consultaCopy(b)).andExpect(status().isUnprocessableEntity());
        lend(bob, quarto).andExpect(status().isUnprocessableEntity());
        lend(user("VISITOR"), circulatingCopy(b)).andExpect(status().isUnprocessableEntity());

        // renovação: 2 permitidas, a 3ª não
        postJson("/api/v1/loans/" + loanIds[1] + "/renew", "").andExpect(status().isOk());
        postJson("/api/v1/loans/" + loanIds[1] + "/renew", "").andExpect(status().isOk());
        postJson("/api/v1/loans/" + loanIds[1] + "/renew", "").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void atrasoGeraMultaNotificaEBloqueiaNovoEmprestimo() throws Exception {
        long ana = user("STUDENT");                 // multa padrão: R$ 1,00 por dia; prazo padrão: 14 dias
        long loan = read(lend(ana, circulatingCopy(book())).andExpect(status().isCreated())).get("id").asLong();

        clock.advanceDays(17);                      // 3 dias depois do vencimento

        postJson("/api/v1/loans/" + loan + "/return", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.fine.amount").value(3.0))
                .andExpect(jsonPath("$.fine.daysLate").value(3))
                .andExpect(jsonPath("$.fine.status").value("ABERTA"));

        Integer avisos = jdbc.queryForObject(
                "select count(*) from notifications where user_id = ? and type = 'FINE_REGISTERED'", Integer.class, ana);
        assertEquals(1, avisos);

        lend(ana, circulatingCopy(book())).andExpect(status().isUnprocessableEntity());   // multa em aberto
    }

    @Test
    void doisBalcoesEmprestandoOMesmoExemplarSoUmPassa() throws Exception {
        long copy = circulatingCopy(book());
        List<Long> leitores = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            leitores.add(user("STUDENT"));
        }
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (long leitor : leitores) {
            tasks.add(() -> lendStatus(leitor, copy));
        }

        List<Integer> statuses = inParallel(tasks);

        assertEquals(1, statuses.stream().filter(s -> s == 201).count());
        assertEquals(4, statuses.stream().filter(s -> s == 422).count());
    }

    @Test
    void emprestimosSimultaneosDoMesmoLeitorNaoPassamDoLimite() throws Exception {
        long ana = user("STUDENT");
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            long copy = circulatingCopy(book());
            tasks.add(() -> lendStatus(ana, copy));
        }

        List<Integer> statuses = inParallel(tasks);

        assertEquals(3, statuses.stream().filter(s -> s == 201).count());
        assertEquals(3, statuses.stream().filter(s -> s == 422).count());
    }
}

package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
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

import br.com.biblioteca.notification.DueDateNotifier;
import br.com.biblioteca.notification.NotificationChannel;
import br.com.biblioteca.notification.NotificationDispatcher;

/** Avisos: criação nos eventos, aviso de vencimento/atraso sem repetir, entrega e tentativas contra PostgreSQL real. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import({EmprestimoFluxoTest.RelogioDeTeste.class, NotificacoesFluxoTest.CanalDeTesteConfig.class})
class NotificacoesFluxoTest {

    /** Canal que guarda o que "enviou" e falha de propósito para mensagens com FALHAR. */
    static class CanalDeTeste implements NotificationChannel {
        final List<String[]> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(String toEmail, String subject, String body) {
            if (body.contains("FALHAR")) {
                throw new IllegalStateException("falha simulada");
            }
            sent.add(new String[] {toEmail, subject, body});
        }
    }

    @TestConfiguration
    static class CanalDeTesteConfig {
        @Bean
        @Primary
        CanalDeTeste canalDeTeste() {
            return new CanalDeTeste();
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
    @Autowired NotificationDispatcher dispatcher;
    @Autowired DueDateNotifier notifier;
    @Autowired CanalDeTeste canal;

    private final ObjectMapper json = new ObjectMapper();
    private static final AtomicInteger seq = new AtomicInteger();
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        String body = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"email\":\"admin@teste.com\",\"password\":\"senha-admin-123\"}"))
                .andReturn().getResponse().getContentAsString();
        admin = "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    private ResultActions send(String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", admin).contentType(JSON).content(body));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private long student(String email) throws Exception {
        return read(send("/api/v1/users", """
                {"fullName":"Leitor","email":"%s","password":"senha-leitor-1","roles":["STUDENT"]}""".formatted(email))
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long lendNewBook(long userId, String title) throws Exception {
        int n = seq.incrementAndGet();
        JsonNode b = read(send("/api/v1/books", """
                {"title":"%s","copies":[{"barcode":"C-%d","kind":"CONSULTA"},{"barcode":"E-%d","kind":"CIRCULANTE"}]}"""
                .formatted(title, n, n)).andExpect(status().isCreated()));
        long copy = 0;
        for (JsonNode c : b.get("copies")) {
            if (c.get("kind").asText().equals("CIRCULANTE")) {
                copy = c.get("id").asLong();
            }
        }
        return read(send("/api/v1/loans", "{\"userId\":" + userId + ",\"copyId\":" + copy + "}")
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private int count(long userId, String type) {
        return jdbc.queryForObject("select count(*) from notifications where user_id = ? and type = ?",
                Integer.class, userId, type);
    }

    @Test
    void emprestimoGeraAvisoQueOCanalEntregaUmaUnicaVez() throws Exception {
        String email = "entrega" + seq.incrementAndGet() + "@teste.com";
        long ana = student(email);
        lendNewBook(ana, "Dom Casmurro");

        assertEquals(1, count(ana, "LOAN_CONFIRMED"));
        dispatcher.dispatchPending();

        List<String[]> paraAna = canal.sent.stream().filter(m -> m[0].equals(email)).toList();
        assertEquals(1, paraAna.size());
        assertEquals("Empréstimo confirmado", paraAna.get(0)[1]);
        assertTrue(paraAna.get(0)[2].contains("Dom Casmurro") && paraAna.get(0)[2].contains("Devolva até"));
        assertNotNull(jdbc.queryForObject("select sent_at from notifications where user_id = ?", java.sql.Timestamp.class, ana));

        dispatcher.dispatchPending();   // nada pendente: não reenvia
        assertEquals(1, canal.sent.stream().filter(m -> m[0].equals(email)).count());
    }

    @Test
    void avisoQueFalhaTentaDeNovoAteOLimiteEFicaRegistrado() throws Exception {
        long ana = student("falha" + seq.incrementAndGet() + "@teste.com");
        jdbc.update("insert into notifications (user_id, type, message) values (?, 'FINE_REGISTERED', 'FALHAR sempre')", ana);

        for (int i = 0; i < NotificationDispatcherLimite.MAX + 2; i++) {
            dispatcher.dispatchPending();
        }

        Map<String, Object> row = jdbc.queryForMap(
                "select attempts, sent_at, last_error from notifications where user_id = ? and message like 'FALHAR%'", ana);
        assertEquals(NotificationDispatcherLimite.MAX, row.get("attempts"));   // parou de tentar
        assertNull(row.get("sent_at"));
        assertTrue(row.get("last_error").toString().contains("falha simulada"));
    }

    /** O limite de tentativas é detalhe interno do despachante; o teste só precisa saber que é finito (5). */
    private static final class NotificationDispatcherLimite {
        static final int MAX = 5;
    }

    @Test
    void administradorTestaOEnvioDeEmailEQuemNaoEAdministradorNaoPode() throws Exception {
        int before = canal.sent.size();
        send("/api/v1/notifications/test", "")
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.to").value("admin@teste.com"));
        assertEquals(before + 1, canal.sent.size());
        assertEquals("admin@teste.com", canal.sent.get(canal.sent.size() - 1)[0]);

        String email = "leitor" + seq.incrementAndGet() + "@teste.com";
        student(email);
        String reader = "Bearer " + read(mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                .content("{\"email\":\"%s\",\"password\":\"senha-leitor-1\"}".formatted(email)))).get("accessToken").asText();
        mvc.perform(post("/api/v1/notifications/test").header("Authorization", reader)).andExpect(status().isForbidden());
    }

    @Test
    void canalForaDoArParaARodadaAposTresFalhasSeguidas() throws Exception {
        jdbc.update("update notifications set attempts = 5 where sent_at is null");   // isola dos outros testes
        long ana = student("fora" + seq.incrementAndGet() + "@teste.com");
        for (int i = 0; i < 5; i++) {
            jdbc.update("insert into notifications (user_id, type, message) values (?, 'FINE_REGISTERED', 'FALHAR ' || ?)", ana, i);
        }

        dispatcher.dispatchPending();

        // só as 3 primeiras foram tentadas; as outras 2 esperam a próxima rodada, sem gastar tentativa
        assertEquals(3, jdbc.queryForObject("select count(*) from notifications where user_id = ? and attempts = 1", Integer.class, ana));
        assertEquals(2, jdbc.queryForObject("select count(*) from notifications where user_id = ? and attempts = 0", Integer.class, ana));
        jdbc.update("delete from notifications where user_id = ?", ana);
    }

    @Test
    void avisoDeVencimentoEAtrasoSaemUmaVezPorEmprestimo() throws Exception {
        long ana = student("prazo" + seq.incrementAndGet() + "@teste.com");
        lendNewBook(ana, "Iracema");                           // prazo padrão: 14 dias

        notifier.run();
        assertEquals(0, count(ana, "LOAN_DUE_SOON"));           // ainda longe do vencimento

        clock.advanceDays(13);                                  // vence amanhã
        notifier.run();
        notifier.run();                                         // repetir a rotina não duplica
        assertEquals(1, count(ana, "LOAN_DUE_SOON"));
        assertEquals(0, count(ana, "LOAN_OVERDUE"));

        clock.advanceDays(2);                                   // venceu ontem
        notifier.run();
        notifier.run();
        assertEquals(1, count(ana, "LOAN_OVERDUE"));
        assertEquals(1, count(ana, "LOAN_DUE_SOON"));

        dispatcher.dispatchPending();
        assertTrue(canal.sent.stream().anyMatch(m -> m[1].equals("Livro em atraso") && m[2].contains("Iracema")));
    }
}

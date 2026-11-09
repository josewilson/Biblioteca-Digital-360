package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.ObjectMapper;

/** "Esqueci a senha" contra PostgreSQL real: link por e-mail, uso único, validade, sessões derrubadas e sem vazar quem tem conta. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class RecuperacaoDeSenhaFluxoTest {

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
        r.add("app.public-url", () -> "https://biblioteca.exemplo.test/");
    }

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final Pattern LINK = Pattern.compile("https://biblioteca\\.exemplo\\.test/redefinir-senha\\?token=([0-9a-f]{64})");

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    br.com.biblioteca.notification.NotificationDispatcher dispatcher;

    private final ObjectMapper json = new ObjectMapper();
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = "Bearer " + json.readTree(call("/api/v1/auth/login", "{\"email\":\"admin@teste.com\",\"password\":\"senha-admin-123\"}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private ResultActions call(String url, String body) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String newUser(String password) throws Exception {
        String email = "esqueci" + SEQ.incrementAndGet() + "@teste.com";
        mvc.perform(post("/api/v1/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Leitor Esquecido","email":"%s","password":"%s","roles":["STUDENT"]}""".formatted(email, password)))
                .andExpect(status().isCreated());
        // tira a obrigação de trocar a senha temporária: aqui interessa só o esquecimento
        jdbc.update("update users set must_change_password = false where email = ?", email);
        return email;
    }

    private void forgot(String email) throws Exception {
        call("/api/v1/auth/forgot", "{\"email\":\"%s\"}".formatted(email)).andExpect(status().isNoContent());
    }

    /** O link está na fila de avisos até ser enviado (nos testes, o envio agendado só roda 30 s depois da subida). */
    private List<String> tokensPending(String email) {
        return jdbc.queryForList("""
                select n.message from notifications n join users u on u.id = n.user_id
                where u.email = ? and n.type = 'PASSWORD_RESET' and n.sent_at is null order by n.id""", String.class, email)
                .stream().map(m -> {
                    Matcher matcher = LINK.matcher(m);
                    assertTrue(matcher.find(), "mensagem sem o link esperado: " + m);
                    return matcher.group(1);
                }).toList();
    }

    private ResultActions reset(String token, String password) throws Exception {
        return call("/api/v1/auth/reset", "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(token, password));
    }

    private ResultActions login(String email, String password) throws Exception {
        return call("/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    @Test
    void linkDefineASenhaNovaUmaVezSoEDerrubaAsSessoesAbertas() throws Exception {
        String email = newUser("senha-antiga-123");
        String oldSession = "Bearer " + json.readTree(login(email, "senha-antiga-123").andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
        forgot(email);
        String token = tokensPending(email).get(0);

        reset(token, "senha-nova-4567").andExpect(status().isNoContent());

        login(email, "senha-nova-4567").andExpect(status().isOk());
        login(email, "senha-antiga-123").andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/loans").header("Authorization", oldSession)).andExpect(status().isUnauthorized());
        reset(token, "outra-senha-9999").andExpect(status().isUnprocessableEntity());   // uso único
        assertEquals(0, jdbc.queryForObject("select count(*) from password_resets where token_hash = ?", Integer.class, token));   // só o hash é guardado
    }

    @Test
    void depoisDeEnviadoOLinkNaoFicaGuardadoNaFila() throws Exception {
        String email = newUser("senha-antiga-123");
        forgot(email);
        String token = tokensPending(email).get(0);

        dispatcher.dispatchPending();   // canal "log" nos testes

        assertEquals(0, tokensPending(email).size());
        assertEquals(0, jdbc.queryForObject("select count(*) from notifications where message like ?", Integer.class, "%" + token + "%"));
        reset(token, "senha-nova-4567").andExpect(status().isNoContent());   // o link já enviado continua valendo
    }

    @Test
    void emailDesconhecidoRecebeAMesmaRespostaENadaVaiParaAFila() throws Exception {
        int before = jdbc.queryForObject("select count(*) from notifications where type = 'PASSWORD_RESET'", Integer.class);

        forgot("ninguem" + SEQ.incrementAndGet() + "@teste.com");

        assertEquals(before, jdbc.queryForObject("select count(*) from notifications where type = 'PASSWORD_RESET'", Integer.class));
    }

    @Test
    void linkExpiradoOuInventadoEhRecusado() throws Exception {
        String email = newUser("senha-antiga-123");
        forgot(email);
        String token = tokensPending(email).get(0);
        jdbc.update("update password_resets set expires_at = now() - interval '1 minute' where user_id = (select id from users where email = ?)", email);

        reset(token, "senha-nova-4567").andExpect(status().isUnprocessableEntity());
        reset("0".repeat(64), "senha-nova-4567").andExpect(status().isUnprocessableEntity());
        login(email, "senha-antiga-123").andExpect(status().isOk());   // a senha antiga continua valendo
    }

    @Test
    void pedidoNovoInvalidaOLinkAnteriorEHaLimiteDePedidosPorHora() throws Exception {
        String email = newUser("senha-antiga-123");
        forgot(email);
        forgot(email);
        List<String> tokens = tokensPending(email);

        reset(tokens.get(0), "senha-nova-4567").andExpect(status().isUnprocessableEntity());   // o primeiro foi substituído

        forgot(email);
        forgot(email);   // o 4º no mesmo período não gera link
        assertEquals(3, tokensPending(email).size());
        reset(tokens.get(1), "senha-nova-4567").andExpect(status().isUnprocessableEntity());   // o 2º também foi substituído pelo 3º
        reset(tokensPending(email).get(2), "senha-nova-4567").andExpect(status().isNoContent());
    }

    @Test
    void contaDesativadaNaoRecebeLinkESenhaCurtaEhRecusada() throws Exception {
        String email = newUser("senha-antiga-123");
        forgot(email);
        String token = tokensPending(email).get(0);

        reset(token, "curta").andExpect(status().isBadRequest());

        jdbc.update("update users set active = false where email = ?", email);
        reset(token, "senha-nova-4567").andExpect(status().isUnprocessableEntity());   // conta fechada depois do pedido
        forgot(email);
        assertFalse(tokensPending(email).size() > 1);
    }
}

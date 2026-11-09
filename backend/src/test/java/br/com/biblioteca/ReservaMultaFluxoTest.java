package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import br.com.biblioteca.loan.ReservationService;

/** Reservas (fila, prazo, cancelamento), multa de funcionário, pagamento e isenção contra PostgreSQL real. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@Import(EmprestimoFluxoTest.RelogioDeTeste.class)
class ReservaMultaFluxoTest {

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
    @Autowired ReservationService reservationService;

    private final ObjectMapper json = new ObjectMapper();
    private static final AtomicInteger seq = new AtomicInteger();
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String PASSWORD = "senha-leitor-1";
    private String admin;

    record Person(long id, String email, String token) {
    }

    @BeforeEach
    void setUp() throws Exception {
        clock.reset();
        admin = login("admin@teste.com", "senha-admin-123");
    }

    // ---------- ajudantes ----------

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    private ResultActions send(String token, String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(JSON).content(body));
    }

    private ResultActions fetch(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", token));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private Person person(String role) throws Exception {
        int n = seq.incrementAndGet();
        String email = "pessoa" + n + "@teste.com";
        long id = read(send(admin, "/api/v1/users", """
                {"fullName":"Pessoa %d","email":"%s","password":"%s","roles":["%s"]}"""
                .formatted(n, email, PASSWORD, role)).andExpect(status().isCreated())).get("id").asLong();
        // Senha inicial é temporária: a equipe só ganha os perfis depois de trocá-la.
        String token = "Bearer " + read(send(login(email, PASSWORD), "/api/v1/auth/password", """
                {"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, PASSWORD + "-nova"))
                .andExpect(status().isOk())).get("accessToken").asText();
        return new Person(id, email, token);
    }

    /** Obra com 1 exemplar de consulta e 1 emprestável; devolve {bookId, circulatingCopyId}. */
    private long[] bookWithOneLendableCopy() throws Exception {
        int n = seq.incrementAndGet();
        JsonNode book = read(send(admin, "/api/v1/books", """
                {"title":"Obra %d","copies":[{"barcode":"C-%d","kind":"CONSULTA"},{"barcode":"E-%d","kind":"CIRCULANTE"}]}"""
                .formatted(n, n, n)).andExpect(status().isCreated()));
        for (JsonNode c : book.get("copies")) {
            if (c.get("kind").asText().equals("CIRCULANTE")) {
                return new long[] {book.get("id").asLong(), c.get("id").asLong()};
            }
        }
        throw new IllegalStateException();
    }

    private ResultActions lend(Person who, long copyId) throws Exception {
        return send(admin, "/api/v1/loans", "{\"userId\":" + who.id() + ",\"copyId\":" + copyId + "}");
    }

    private long lendOk(Person who, long copyId) throws Exception {
        return read(lend(who, copyId).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions giveBack(long loanId) throws Exception {
        return send(admin, "/api/v1/loans/" + loanId + "/return", "");
    }

    private long reserveFor(Person who, long bookId) throws Exception {
        return read(send(admin, "/api/v1/reservations", "{\"userId\":" + who.id() + ",\"bookId\":" + bookId + "}")
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode reservationOf(Person who) throws Exception {
        return read(fetch(admin, "/api/v1/reservations?userId=" + who.id()).andExpect(status().isOk())).get("content").get(0);
    }

    /** Pega o livro, deixa vencer em 3 dias e devolve atrasado; devolve a multa gerada. */
    private JsonNode lateReturn(Person who) throws Exception {
        long[] book = bookWithOneLendableCopy();
        long loan = lendOk(who, book[1]);
        clock.advanceDays(17);   // prazo padrão 14 dias
        JsonNode fine = read(giveBack(loan).andExpect(status().isOk())).get("fine");
        clock.reset();
        return fine;
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

    // ---------- reservas ----------

    @Test
    void filaDeReservasGuardaOExemplarParaOPrimeiroERespeitaADonoDaReserva() throws Exception {
        Person ana = person("STUDENT"), bob = person("TEACHER"), carla = person("STUDENT");
        long[] book = bookWithOneLendableCopy();
        long loan = lendOk(ana, book[1]);

        long bobReservation = reserveFor(bob, book[0]);
        send(carla.token(), "/api/v1/me/reservations", "{\"bookId\":" + book[0] + "}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.queuePosition").value(2));
        fetch(admin, "/api/v1/reservations?userId=" + bob.id()).andExpect(jsonPath("$.content[0].queuePosition").value(1));

        // regras de reserva
        send(admin, "/api/v1/reservations", "{\"userId\":" + bob.id() + ",\"bookId\":" + book[0] + "}")
                .andExpect(status().isUnprocessableEntity());                         // duplicada
        long[] livre = bookWithOneLendableCopy();
        reserveForExpecting422(bob, livre[0]);                                        // há exemplar livre
        send(person("VISITOR").token(), "/api/v1/me/reservations", "{\"bookId\":" + book[0] + "}")
                .andExpect(status().isUnprocessableEntity());                         // visitante não reserva
        send(admin, "/api/v1/loans/" + loan + "/renew", "").andExpect(status().isUnprocessableEntity());  // fila impede renovar

        // devolução entrega o exemplar ao 1º da fila
        giveBack(loan).andExpect(status().isOk());
        JsonNode bobNow = reservationOf(bob);
        assertEquals("DISPONIVEL", bobNow.get("status").asText());
        assertEquals(book[1], bobNow.get("copyId").asLong());
        assertEquals(1, reservationOf(carla).get("queuePosition").asInt());

        lend(carla, book[1]).andExpect(status().isUnprocessableEntity());             // guardado para o Bob
        lend(bob, book[1]).andExpect(status().isCreated());
        assertEquals("RETIRADA", reservationOf(bob).get("status").asText());
        assertEquals(bobReservation, reservationOf(bob).get("id").asLong());
    }

    private void reserveForExpecting422(Person who, long bookId) throws Exception {
        send(admin, "/api/v1/reservations", "{\"userId\":" + who.id() + ",\"bookId\":" + bookId + "}")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reservaNaoRetiradaNoPrazoExpiraEOExemplarVaiParaOProximo() throws Exception {
        Person ana = person("STUDENT"), dave = person("STUDENT"), eve = person("TEACHER");
        long[] book = bookWithOneLendableCopy();
        long loan = lendOk(ana, book[1]);
        reserveFor(dave, book[0]);
        reserveFor(eve, book[0]);
        giveBack(loan).andExpect(status().isOk());
        assertEquals("DISPONIVEL", reservationOf(dave).get("status").asText());

        clock.advanceDays(4);                                                         // prazo padrão de retirada: 3 dias
        lend(dave, book[1]).andExpect(status().isUnprocessableEntity());              // expirou: recusa na hora
        reservationService.expireOverdue();                                           // varredura agendada

        assertEquals("EXPIRADA", reservationOf(dave).get("status").asText());
        assertEquals("DISPONIVEL", reservationOf(eve).get("status").asText());
        assertEquals(book[1], reservationOf(eve).get("copyId").asLong());
    }

    @Test
    void cancelarReservaComExemplarGuardadoPassaOExemplarAoProximo() throws Exception {
        Person ana = person("STUDENT"), bob = person("STUDENT"), carla = person("STUDENT");
        long[] book = bookWithOneLendableCopy();
        long loan = lendOk(ana, book[1]);
        long bobReservation = reserveFor(bob, book[0]);
        reserveFor(carla, book[0]);
        giveBack(loan).andExpect(status().isOk());

        send(carla.token(), "/api/v1/me/reservations/" + bobReservation + "/cancel", "")
                .andExpect(status().isNotFound());                                    // não é dela
        send(bob.token(), "/api/v1/me/reservations/" + bobReservation + "/cancel", "")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELADA"));

        assertEquals("DISPONIVEL", reservationOf(carla).get("status").asText());
    }

    @Test
    void exemplarNovoOuRestauradoAtendeAFila() throws Exception {
        Person ana = person("STUDENT"), bob = person("STUDENT");
        long[] book = bookWithOneLendableCopy();
        lendOk(ana, book[1]);
        reserveFor(bob, book[0]);

        send(admin, "/api/v1/books/" + book[0] + "/copies", "{\"barcode\":\"NOVO-" + seq.incrementAndGet() + "\",\"kind\":\"CIRCULANTE\"}")
                .andExpect(status().isCreated());

        assertEquals("DISPONIVEL", reservationOf(bob).get("status").asText());
    }

    // ---------- multa, pagamento e isenção ----------

    @Test
    void funcionarioAtrasadoPagaMulta() throws Exception {
        JsonNode fine = lateReturn(person("EMPLOYEE"));

        assertEquals(3.0, fine.get("amount").asDouble(), 0.001);   // R$ 1,00 por dia (padrão assumido) x 3 dias
        assertEquals("ABERTA", fine.get("status").asText());
    }

    @Test
    void auxiliarRecebePagamentoUmaUnicaVezELiberaOLeitor() throws Exception {
        Person ana = person("STUDENT");
        Person assistant = person("ASSISTANT");
        long fineId = lateReturn(ana).get("id").asLong();
        lend(ana, bookWithOneLendableCopy()[1]).andExpect(status().isUnprocessableEntity());   // multa em aberto

        long payment = read(send(assistant.token(), "/api/v1/fines/" + fineId + "/payments", "{\"method\":\"PIX\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.amount").value(3.0))).get("id").asLong();
        fetch(assistant.token(), "/api/v1/payments/" + payment).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("PIX"));
        send(assistant.token(), "/api/v1/fines/" + fineId + "/payments", "{\"method\":\"PIX\"}")
                .andExpect(status().isUnprocessableEntity());                                   // sem pagamento duplo

        fetch(ana.token(), "/api/v1/me/fines").andExpect(jsonPath("$.content[0].status").value("PAGA"));
        lend(ana, bookWithOneLendableCopy()[1]).andExpect(status().isCreated());
    }

    @Test
    void doisBalcoesPagandoAMesmaMultaSoUmPassa() throws Exception {
        long fineId = lateReturn(person("STUDENT")).get("id").asLong();
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> send(admin, "/api/v1/fines/" + fineId + "/payments", "{\"method\":\"DINHEIRO\"}")
                    .andReturn().getResponse().getStatus());
        }

        List<Integer> statuses = inParallel(tasks);

        assertEquals(1, statuses.stream().filter(s -> s == 201).count());
        assertEquals(3, statuses.stream().filter(s -> s == 422).count());
    }

    @Test
    void soAdministradorEBibliotecarioIsentamEComJustificativa() throws Exception {
        Person ana = person("STUDENT");
        Person assistant = person("ASSISTANT");
        Person librarian = person("LIBRARIAN");
        long fineId = lateReturn(ana).get("id").asLong();

        send(assistant.token(), "/api/v1/fines/" + fineId + "/waive", "{\"reason\":\"motivo\"}")
                .andExpect(status().isForbidden());
        send(librarian.token(), "/api/v1/fines/" + fineId + "/waive", "{\"reason\":\"\"}")
                .andExpect(status().isBadRequest());
        send(librarian.token(), "/api/v1/fines/" + fineId + "/waive", "{\"reason\":\"Doença comprovada\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ISENTA"))
                .andExpect(jsonPath("$.waiverReason").value("Doença comprovada"));

        send(admin, "/api/v1/fines/" + fineId + "/payments", "{\"method\":\"PIX\"}")
                .andExpect(status().isUnprocessableEntity());                                   // isenta não se paga
        lend(ana, bookWithOneLendableCopy()[1]).andExpect(status().isCreated());                // e libera o leitor
    }
}

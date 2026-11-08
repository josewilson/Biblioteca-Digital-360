package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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

import jakarta.persistence.EntityManagerFactory;

/**
 * Guarda contra o problema N+1: cada tela de lista deve custar um número FIXO de consultas, não uma por linha.
 * Mede as consultas JPA reais (estatísticas do Hibernate) com 12 linhas por lista.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@Import(EmprestimoFluxoTest.RelogioDeTeste.class)
class ConsultasPorRequisicaoTest {

    private static final int ROWS = 12;

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
    @Autowired EntityManagerFactory emf;

    private final ObjectMapper json = new ObjectMapper();
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private String admin;

    private ResultActions send(String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", admin).contentType(JSON).content(body));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    /** Executa o GET e devolve quantas consultas o JPA fez. */
    private long statementsFor(String url) throws Exception {
        Statistics stats = emf.unwrap(SessionFactory.class).getStatistics();
        stats.clear();
        mvc.perform(get(url).header("Authorization", admin)).andExpect(status().isOk());
        return stats.getPrepareStatementCount();
    }

    private void assertAtMost(String url, long max) throws Exception {
        long actual = statementsFor(url);
        System.out.println("CONSULTAS " + url + " -> " + actual + " (máximo " + max + ")");
        assertTrue(actual > 0, url + ": o contador não registrou nenhuma consulta (estatísticas desligadas?)");
        assertTrue(actual <= max, url + " fez " + actual + " consultas (máximo " + max + "): provável N+1");
    }

    @Test
    void listasCustamUmNumeroFixoDeConsultasEnaoUmaPorLinha() throws Exception {
        clock.reset();
        String body = mvc.perform(post("/api/v1/auth/login").contentType(JSON)
                        .content("{\"email\":\"admin@teste.com\",\"password\":\"senha-admin-123\"}"))
                .andReturn().getResponse().getContentAsString();
        admin = "Bearer " + json.readTree(body).get("accessToken").asText();

        long publisher = read(send("/api/v1/publishers", "{\"name\":\"Editora\"}")).get("id").asLong();
        long[] loanIds = new long[ROWS];
        long[] bookIds = new long[ROWS];
        for (int i = 0; i < ROWS; i++) {
            long borrower = read(send("/api/v1/users", """
                    {"fullName":"Leitor %d","email":"leitor%d@t.com","password":"senha-leitor-1","roles":["STUDENT"]}"""
                    .formatted(i, i)).andExpect(status().isCreated())).get("id").asLong();
            long waiting = read(send("/api/v1/users", """
                    {"fullName":"Espera %d","email":"espera%d@t.com","password":"senha-leitor-1","roles":["STUDENT"]}"""
                    .formatted(i, i)).andExpect(status().isCreated())).get("id").asLong();
            JsonNode book = read(send("/api/v1/books", """
                    {"title":"Obra %d","publisherId":%d,"copies":[{"barcode":"C-%d","kind":"CONSULTA"},{"barcode":"E-%d","kind":"CIRCULANTE"}]}"""
                    .formatted(i, publisher, i, i)).andExpect(status().isCreated()));
            bookIds[i] = book.get("id").asLong();
            long copy = book.get("copies").get(1).get("id").asLong();
            loanIds[i] = read(send("/api/v1/loans", "{\"userId\":" + borrower + ",\"copyId\":" + copy + "}")
                    .andExpect(status().isCreated())).get("id").asLong();
            send("/api/v1/reservations", "{\"userId\":" + waiting + ",\"bookId\":" + bookIds[i] + "}")
                    .andExpect(status().isCreated());
        }
        clock.advanceDays(17);   // 2 devoluções com atraso geram 2 multas
        for (int i = 0; i < 2; i++) {
            send("/api/v1/loans/" + loanIds[i] + "/return", "").andExpect(status().isOk());
        }

        assertAtMost("/api/v1/loans?size=20&sort=id,desc", 3);               // 1 (página) ou 2 (com contagem); antes eram 2 por linha
        assertAtMost("/api/v1/loans?activeOnly=true&size=20&sort=dueAt,asc", 3);
        assertAtMost("/api/v1/users?size=200&sort=fullName", 4);             // 25 usuários: perfis em lotes, não 1 por usuário
        assertAtMost("/api/v1/books?size=20&sort=title", 6);                 // editora junto; autores/categorias/exemplares em lotes
        assertAtMost("/api/v1/reservations?size=20&sort=id,desc", 4);        // posições na fila numa só consulta
        assertAtMost("/api/v1/fines?size=20&sort=id,desc", 3);
    }
}

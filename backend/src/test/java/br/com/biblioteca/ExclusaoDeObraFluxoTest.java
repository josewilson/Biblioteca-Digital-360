package br.com.biblioteca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

/** Exclusão de obra contra PostgreSQL real: só sem histórico, só administrador, exemplares vão junto. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ExclusaoDeObraFluxoTest {

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

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String PASSWORD = "Senha-inicial-123";

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();
    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@teste.com", "senha-admin-123");
    }

    private String login(String email, String password) throws Exception {
        return "Bearer " + read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))).andExpect(status().isOk()))
                .get("accessToken").asText();
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(String token, String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions remove(String token, long bookId) throws Exception {
        return mvc.perform(delete("/api/v1/books/" + bookId).header("Authorization", token));
    }

    /** Devolve {bookId, circulatingCopyId, consultaCopyId}. */
    private long[] newBook() throws Exception {
        int n = SEQ.incrementAndGet();
        JsonNode book = read(send(admin, "/api/v1/books", """
                {"title":"Obra a excluir %d","copies":[{"barcode":"EXC-%d-C","kind":"CONSULTA"},{"barcode":"EXC-%d-E","kind":"CIRCULANTE"}]}"""
                .formatted(n, n, n)).andExpect(status().isCreated()));
        long circ = 0;
        long consulta = 0;
        for (JsonNode c : book.get("copies")) {
            if (c.get("kind").asText().equals("CIRCULANTE")) {
                circ = c.get("id").asLong();
            } else {
                consulta = c.get("id").asLong();
            }
        }
        return new long[] {book.get("id").asLong(), circ, consulta};
    }

    /** Pessoa com a senha inicial já trocada (a equipe só ganha perfis depois disso). */
    private String person(String role, long[] idOut) throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "exclusao" + n + "@teste.com";
        idOut[0] = read(send(admin, "/api/v1/users", """
                {"fullName":"Pessoa %d","email":"%s","password":"%s","roles":["%s"]}"""
                .formatted(n, email, PASSWORD, role)).andExpect(status().isCreated())).get("id").asLong();
        return "Bearer " + read(send(login(email, PASSWORD), "/api/v1/auth/password", """
                {"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, PASSWORD + "-nova"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    @Test
    void excluiObraSemHistoricoComSeusExemplares() throws Exception {
        long[] b = newBook();

        remove(admin, b[0]).andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/books/" + b[0]).header("Authorization", admin)).andExpect(status().isNotFound());
        remove(admin, b[0]).andExpect(status().isNotFound());
        // o código de barras fica livre para uma obra nova
        send(admin, "/api/v1/books", """
                {"title":"Outra","copies":[{"barcode":"EXC-%d-C","kind":"CONSULTA"},{"barcode":"EXC-%d-E","kind":"CIRCULANTE"}]}"""
                .formatted(SEQ.get(), SEQ.get())).andExpect(status().isCreated());
    }

    @Test
    void recusaObraComEmprestimoNoHistoricoMesmoDevolvido() throws Exception {
        long[] b = newBook();
        long[] reader = new long[1];
        person("STUDENT", reader);
        long loan = read(send(admin, "/api/v1/loans", "{\"userId\":" + reader[0] + ",\"copyId\":" + b[1] + "}")
                .andExpect(status().isCreated())).get("id").asLong();

        remove(admin, b[0]).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("histórico")));

        send(admin, "/api/v1/loans/" + loan + "/return", "").andExpect(status().isOk());
        remove(admin, b[0]).andExpect(status().isUnprocessableEntity());   // devolvido, mas o histórico continua

        mvc.perform(get("/api/v1/books/" + b[0]).header("Authorization", admin)).andExpect(status().isOk());
    }

    @Test
    void soAdministradorExclui() throws Exception {
        long[] b = newBook();
        String librarian = person("LIBRARIAN", new long[1]);
        String student = person("STUDENT", new long[1]);

        remove(librarian, b[0]).andExpect(status().isForbidden());
        remove(student, b[0]).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/books/" + b[0])).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/books/" + b[0]).header("Authorization", admin)).andExpect(status().isOk());
    }
}

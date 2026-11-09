package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/** Acervo de ponta a ponta contra PostgreSQL real. Pulado sem Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class AcervoFluxoTest {

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

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private String adminToken() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@teste.com\",\"password\":\"senha-admin-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("accessToken").asText();
    }

    private ResultActions postJson(String url, String token, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    @Test
    void cadastraObraComExemplaresERespeitaARegraDeConsulta() throws Exception {
        String token = adminToken();
        long autor = read(postJson("/api/v1/authors", token, "{\"name\":\"Machado de Assis\"}")
                .andExpect(status().isCreated())).get("id").asLong();
        long editora = read(postJson("/api/v1/publishers", token, "{\"name\":\"Garnier\"}")
                .andExpect(status().isCreated())).get("id").asLong();
        long categoria = read(postJson("/api/v1/categories", token, "{\"name\":\"Romance\"}")
                .andExpect(status().isCreated())).get("id").asLong();

        String obra = """
                {"title":"Dom Casmurro","isbn":"9788535910663","publicationYear":1899,"language":"pt",
                 "publisherId":%d,"authorIds":[%d],"categoryIds":[%d],
                 "copies":[{"barcode":"DC-1","kind":"CONSULTA","location":"Estante A"},
                           {"barcode":"DC-2","kind":"CIRCULANTE","condition":"NOVO"}]}""".formatted(editora, autor, categoria);
        JsonNode criada = read(postJson("/api/v1/books", token, obra).andExpect(status().isCreated())
                .andExpect(jsonPath("$.publisher").value("Garnier")));
        assertEquals(2, criada.get("copies").size());
        long bookId = criada.get("id").asLong();

        // obra sem exemplar de consulta é recusada
        postJson("/api/v1/books", token, """
                {"title":"Sem consulta","copies":[{"barcode":"X-1","kind":"CIRCULANTE"},{"barcode":"X-2","kind":"CIRCULANTE"}]}""")
                .andExpect(status().isUnprocessableEntity());

        // não se dá baixa no único exemplar de consulta...
        long consulta = criada.get("copies").get(0).get("id").asLong();
        mvc.perform(patch("/api/v1/copies/" + consulta + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BAIXADO\"}"))
                .andExpect(status().isUnprocessableEntity());

        // ...mas depois de cadastrar outro de consulta, sim
        postJson("/api/v1/books/" + bookId + "/copies", token, "{\"barcode\":\"DC-3\",\"kind\":\"CONSULTA\"}")
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/copies/" + consulta + "/status").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"BAIXADO\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("BAIXADO"));

        mvc.perform(get("/api/v1/books?q=casmurro").header("Authorization", token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].copies.length()").value(3));
    }
}

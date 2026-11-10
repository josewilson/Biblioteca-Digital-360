package br.com.biblioteca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import tools.jackson.databind.ObjectMapper;

/** Especificação OpenAPI: sai em JSON, descreve a API de verdade e só o administrador a lê. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class DocumentacaoDaApiFluxoTest {

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

    private static final String PASSWORD = "Senha-inicial-123";

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    private String login(String email, String password) throws Exception {
        return "Bearer " + json.readTree(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
    }

    private ResultActions spec(String token) throws Exception {
        return token == null ? mvc.perform(get("/api/v1/openapi"))
                : mvc.perform(get("/api/v1/openapi").header("Authorization", token));
    }

    @Test
    void administradorLeAEspecificacaoComOsEndpointsEOEsquemaBearer() throws Exception {
        spec(login("admin@teste.com", "senha-admin-123")).andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("Biblioteca Digital 360 — API"))
                .andExpect(jsonPath("$.paths['/api/v1/books']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/books/{id}/cover/lookup'].post").exists())
                .andExpect(jsonPath("$.paths['/api/v1/loans']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }

    @Test
    void semLoginOuSemPerfilDeAdministradorNaoLe() throws Exception {
        spec(null).andExpect(status().isUnauthorized());

        String admin = login("admin@teste.com", "senha-admin-123");
        mvc.perform(post("/api/v1/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Bibliotecária\",\"email\":\"bib@teste.com\",\"password\":\"%s\",\"roles\":[\"LIBRARIAN\"]}"
                        .formatted(PASSWORD))).andExpect(status().isCreated());
        String librarian = "Bearer " + json.readTree(mvc.perform(post("/api/v1/auth/password")
                        .header("Authorization", login("bib@teste.com", PASSWORD)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(PASSWORD, PASSWORD + "-nova")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("accessToken").asText();

        spec(librarian).andExpect(status().isForbidden());
    }
}

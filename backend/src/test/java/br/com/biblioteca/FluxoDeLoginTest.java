package br.com.biblioteca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Sobe a aplicação inteira contra PostgreSQL real: valida o mapeamento JPA, as migrações, o login e o cadastro.
 * Pulado automaticamente sem Docker.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class FluxoDeLoginTest {

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

    private String login(String email, String senha) throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + senha + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode json = new ObjectMapper().readTree(body);
        return json.get("accessToken").asText();
    }

    @Test
    void adminLogaCadastraAlunoEOAlunoNaoListaUsuarios() throws Exception {
        String admin = login("admin@teste.com", "senha-admin-123");

        mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ana Souza","email":"ana@teste.com","password":"senha-aluna-1","roles":["STUDENT"]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        String aluna = login("ana@teste.com", "senha-aluna-1");
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + aluna))
                .andExpect(status().isForbidden());
    }

    @Test
    void cincoSenhasErradasBloqueiamOLoginDaqueleEmailMesmoComASenhaCerta() throws Exception {
        String admin = login("admin@teste.com", "senha-admin-123");
        for (String email : new String[] {"alvo@teste.com", "outro@teste.com"}) {
            mvc.perform(post("/api/v1/users").header("Authorization", "Bearer " + admin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Leitor\",\"email\":\"" + email + "\",\"password\":\"senha-certa-1\",\"roles\":[\"STUDENT\"]}"))
                    .andExpect(status().isCreated());
        }
        String errada = "{\"email\":\"alvo@teste.com\",\"password\":\"errada\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(errada))
                    .andExpect(status().isUnauthorized());
        }

        // 6ª tentativa, agora com a senha CERTA: bloqueada (429), sem token
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"alvo@teste.com\",\"password\":\"senha-certa-1\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        // outro e-mail não é afetado
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outro@teste.com\",\"password\":\"senha-certa-1\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void senhaErradaRecebe401() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@teste.com\",\"password\":\"errada\"}"))
                .andExpect(status().isUnauthorized());
    }
}

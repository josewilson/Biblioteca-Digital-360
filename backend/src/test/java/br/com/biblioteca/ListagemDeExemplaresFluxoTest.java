package br.com.biblioteca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
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

/** Listagem de exemplares (GET /copies) contra PostgreSQL real: busca sem acento, filtros e acesso só da equipe. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ListagemDeExemplaresFluxoTest {

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

    private long create(String url, String body) throws Exception {
        return read(send(admin, url, body).andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode list(String query) throws Exception {
        return read(mvc.perform(get("/api/v1/copies?" + query).header("Authorization", admin)).andExpect(status().isOk()));
    }

    private List<String> barcodes(String query) throws Exception {
        List<String> out = new ArrayList<>();
        list(query).get("content").forEach(c -> out.add(c.get("barcode").asText()));
        return out;
    }

    /** Duas obras de nomes únicos neste teste: A (autor, categoria, editora próprios) e B (só título). */
    private record Seed(String tag, String isbn, long bookA, long copyAE, long copyAC) { }

    private Seed seed() throws Exception {
        int n = SEQ.incrementAndGet();
        String tag = "zq" + n + "x";
        String isbn = "978-85-" + (1000 + n);
        long author = create("/api/v1/authors", "{\"name\":\"Ângela " + tag + " Müller\"}");
        long category = create("/api/v1/categories", "{\"name\":\"Matemática " + tag + "\"}");
        long publisher = create("/api/v1/publishers", "{\"name\":\"Editora " + tag + "\"}");
        JsonNode a = read(send(admin, "/api/v1/books", """
                {"title":"Álgebra %s","isbn":"%s","publisherId":%d,"authorIds":[%d],"categoryIds":[%d],
                 "copies":[{"barcode":"%s-C","kind":"CONSULTA","location":"Sala %s"},{"barcode":"%s-E","kind":"CIRCULANTE"}]}"""
                .formatted(tag, isbn, publisher, author, category, tag, tag, tag)).andExpect(status().isCreated()));
        long ae = 0;
        long ac = 0;
        for (JsonNode c : a.get("copies")) {
            if (c.get("kind").asText().equals("CIRCULANTE")) {
                ae = c.get("id").asLong();
            } else {
                ac = c.get("id").asLong();
            }
        }
        send(admin, "/api/v1/books", """
                {"title":"Outro livro %s","copies":[{"barcode":"B-%s-C","kind":"CONSULTA"},{"barcode":"B-%s-E","kind":"CIRCULANTE"}]}"""
                .formatted(tag, tag, tag)).andExpect(status().isCreated());
        return new Seed(tag, isbn, a.get("id").asLong(), ae, ac);
    }

    @Test
    void buscaPorTituloAutorEditoraCategoriaCodigoELocalSemAcento() throws Exception {
        Seed s = seed();
        List<String> obraA = List.of(s.tag + "-C", s.tag + "-E");

        for (String q : new String[] {"algebra " + s.tag, "angela " + s.tag, "muller", "editora " + s.tag,
                "matematica " + s.tag, s.tag + "-e", "sala " + s.tag}) {
            // título, autor (sem acento), editora, categoria ("disciplina"), código parcial sem caixa, localização
            org.assertj.core.api.Assertions.assertThat(barcodes("q=" + q))
                    .as(q).containsAll(q.equals(s.tag + "-e") ? List.of(s.tag + "-E") : q.startsWith("sala") ? List.of(s.tag + "-C") : obraA);   // localização só no exemplar de consulta
        }
        // ISBN com hífen digitado ou sem
        org.assertj.core.api.Assertions.assertThat(barcodes("q=" + s.isbn)).contains(s.tag + "-E");
        org.assertj.core.api.Assertions.assertThat(barcodes("q=nada-que-exista-" + s.tag)).isEmpty();
    }

    @Test
    void filtraPorSituacaoTipoECategoriaECombinaOsFiltros() throws Exception {
        Seed s = seed();
        mvc.perform(patch("/api/v1/copies/" + s.copyAE + "/status").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PERDIDO\"}")).andExpect(status().isOk());

        org.assertj.core.api.Assertions.assertThat(barcodes("q=" + s.tag + "&status=PERDIDO")).containsExactly(s.tag + "-E");
        org.assertj.core.api.Assertions.assertThat(barcodes("q=" + s.tag + "&status=DISPONIVEL")).contains(s.tag + "-C")
                .doesNotContain(s.tag + "-E");
        org.assertj.core.api.Assertions.assertThat(barcodes("q=" + s.tag + "&kind=CONSULTA"))
                .contains(s.tag + "-C", "B-" + s.tag + "-C").doesNotContain(s.tag + "-E");

        // categoria: só a obra A tem
        long categoryId = categoryIdOf(s.tag);
        org.assertj.core.api.Assertions.assertThat(barcodes("categoryId=" + categoryId))
                .containsExactlyInAnyOrder(s.tag + "-C", s.tag + "-E");
    }

    private long categoryIdOf(String tag) throws Exception {
        for (JsonNode c : read(mvc.perform(get("/api/v1/categories").header("Authorization", admin)))) {
            if (c.get("name").asText().contains(tag)) {
                return c.get("id").asLong();
            }
        }
        throw new AssertionError("categoria não encontrada");
    }

    @Test
    void devolveOExemplarComOsDadosDaObraPaginadoEOrdenadoPorTitulo() throws Exception {
        Seed s = seed();
        JsonNode page = list("q=" + s.tag + "&size=2");
        org.assertj.core.api.Assertions.assertThat(page.get("totalElements").asLong()).isEqualTo(4);
        org.assertj.core.api.Assertions.assertThat(page.get("content")).hasSize(2);

        JsonNode item = page.get("content").get(0);
        org.assertj.core.api.Assertions.assertThat(item.get("title").asText()).isEqualTo("Álgebra " + s.tag);   // "Á" < "O" sem acento
        org.assertj.core.api.Assertions.assertThat(item.get("bookId").asLong()).isEqualTo(s.bookA);
        org.assertj.core.api.Assertions.assertThat(item.get("authors").get(0).asText()).contains("Müller");
        org.assertj.core.api.Assertions.assertThat(item.get("categories").get(0).asText()).contains("Matemática");
        org.assertj.core.api.Assertions.assertThat(item.get("publisher").asText()).isEqualTo("Editora " + s.tag);
    }

    @Test
    void soAEquipeListaExemplaresEFiltroInvalidoDa400() throws Exception {
        mvc.perform(get("/api/v1/copies")).andExpect(status().isUnauthorized());
        long[] id = new long[1];
        String student = person("STUDENT", id);
        mvc.perform(get("/api/v1/copies").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/copies?status=XYZ").header("Authorization", admin)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/copies?size=1000").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(org.hamcrest.Matchers.lessThanOrEqualTo(100)));
    }

    private String person(String role, long[] idOut) throws Exception {
        int n = SEQ.incrementAndGet();
        String email = "listagem" + n + "@teste.com";
        idOut[0] = create("/api/v1/users", """
                {"fullName":"Pessoa %d","email":"%s","password":"%s","roles":["%s"]}""".formatted(n, email, PASSWORD, role));
        return "Bearer " + read(send(login(email, PASSWORD), "/api/v1/auth/password", """
                {"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, PASSWORD + "-nova"))
                .andExpect(status().isOk())).get("accessToken").asText();
    }
}

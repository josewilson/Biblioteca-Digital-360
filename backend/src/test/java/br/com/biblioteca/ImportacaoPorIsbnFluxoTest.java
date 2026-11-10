package br.com.biblioteca;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Consulta por ISBN contra PostgreSQL real, com a BrasilAPI e a Open Library simuladas (nenhuma chamada à internet).
 * O ISBN escolhe o comportamento do provedor falso; o cadastro nunca é alterado pela consulta.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ImportacaoPorIsbnFluxoTest {

    // ISBN-13 válidos, só para escolher o comportamento do provedor falso.
    static final String AMBOS = "9781000000009";        // os dois conhecem o livro
    static final String SO_OPEN_LIBRARY = "9782000000006";   // BrasilAPI 404; Open Library responde por redirecionamento
    static final String NINGUEM = "9783000000003";      // 404 nos dois
    static final String BRASIL_LENTA = "9784000000000";   // BrasilAPI passa do tempo; Open Library responde
    static final String TUDO_QUEBRADO = "9785000000007";   // 500 nos dois
    static final String JA_CADASTRADO = "9788545702870";   // cadastrado como ISBN-10 com hífen

    static final AtomicInteger BRASIL_CALLS = new AtomicInteger();
    static final HttpServer PROVIDERS = startProviders();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static void reply(HttpExchange x, int status, String type, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().add("Content-Type", type);
        x.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            x.getResponseBody().write(bytes);
        }
        x.close();
    }

    private static HttpServer startProviders() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.setExecutor(Executors.newCachedThreadPool());   // a resposta lenta não pode travar as outras
            server.createContext("/brasil/", x -> {
                BRASIL_CALLS.incrementAndGet();
                String isbn = x.getRequestURI().getPath().substring("/brasil/".length());
                switch (isbn) {
                    case AMBOS -> reply(x, 200, "application/json", """
                            {"isbn":"%s","title":"Livro Importado","subtitle":"Teste","authors":["MARIA TESTE","Outro Autor"],
                             "publisher":"Editora Exemplo Ltda","synopsis":"<p>Resumo &amp; mais</p>","year":2020,
                             "cover_url":"http://exemplo.test/capa.jpg",
                             "subjects":["Ficção","assunto um","assunto dois","assunto três","assunto quatro","assunto cinco","assunto seis"]}"""
                            .formatted(isbn));
                    case SO_OPEN_LIBRARY -> reply(x, 404, "application/json", "{\"message\":\"nao achei\"}");
                    case NINGUEM -> reply(x, 400, "application/json", "{\"message\":\"ISBN inválido\",\"type\":\"bad_request\"}");   // como a BrasilAPI real
                    case BRASIL_LENTA -> {
                        try {
                            Thread.sleep(3000);   // o timeout configurado é 1 s
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        reply(x, 200, "application/json", "{\"title\":\"Tarde demais\"}");
                    }
                    default -> reply(x, 500, "text/plain", "erro");
                }
            });
            server.createContext("/ol/", x -> {
                String path = x.getRequestURI().getPath();
                if (path.startsWith("/ol/books/")) {   // destino do redirecionamento
                    reply(x, 200, "application/json", """
                            {"title":"Livro da Open Library","subtitle":"Sub","publishers":["Editora OL"],"publish_date":"Jun, 2019",
                             "languages":[{"key":"/languages/por"}],"covers":[123],"description":{"type":"/type/text","value":"Descrição da OL"}}""");
                    return;
                }
                String isbn = path.substring("/ol/".length()).replace(".json", "");
                switch (isbn) {
                    case AMBOS -> reply(x, 200, "application/json", """
                            {"title":"Outro título","languages":[{"key":"/languages/eng"}],"covers":[456],"description":"Descrição da OL"}""");
                    case SO_OPEN_LIBRARY -> {
                        x.getResponseHeaders().add("Location", "/ol/books/OL1M.json");
                        x.sendResponseHeaders(302, -1);
                        x.close();
                    }
                    case BRASIL_LENTA -> reply(x, 200, "application/json", "{\"title\":\"Só a Open Library\"}");
                    case NINGUEM -> reply(x, 404, "application/json", "{}");
                    default -> reply(x, 500, "text/plain", "erro");
                }
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        String base = "http://localhost:" + PROVIDERS.getAddress().getPort();
        r.add("app.isbn.brasilapi-url", () -> base + "/brasil/%s");
        r.add("app.isbn.openlibrary-url", () -> base + "/ol/%s.json");
        r.add("app.isbn.timeout", () -> "1s");
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

    private ResultActions lookup(String token, String isbn) throws Exception {
        return mvc.perform(get("/api/v1/books/isbn/" + isbn).header("Authorization", token));
    }

    private long create(String url, String name) throws Exception {
        return read(send(admin, url, "{\"name\":\"" + name + "\"}").andExpect(status().isCreated())).get("id").asLong();
    }

    @Test
    void juntaOsDoisProvedoresCasaComOCadastroENaoGravaNada() throws Exception {
        long publisher = create("/api/v1/publishers", "Exemplo");
        long author = create("/api/v1/authors", "Teste, Maria");
        long category = create("/api/v1/categories", "Ficção");

        lookup(admin, AMBOS).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRegistered").value(false))
                .andExpect(jsonPath("$.isbn").value(AMBOS))
                .andExpect(jsonPath("$.title").value("Livro Importado: Teste"))
                .andExpect(jsonPath("$.publicationYear").value(2020))
                .andExpect(jsonPath("$.language").value("Inglês"))   // a BrasilAPI não traz idioma: vem da Open Library
                .andExpect(jsonPath("$.description").value("Resumo & mais"))
                .andExpect(jsonPath("$.coverUrl").value("https://exemplo.test/capa.jpg"))
                .andExpect(jsonPath("$.edition").doesNotExist())
                .andExpect(jsonPath("$.publisher.id").value(publisher))
                .andExpect(jsonPath("$.authors[0].id").value(author))
                .andExpect(jsonPath("$.authors[1].id").doesNotExist())
                .andExpect(jsonPath("$.authors[1].name").value("Outro Autor"))
                .andExpect(jsonPath("$.categories[0].id").value(category))
                .andExpect(jsonPath("$.categories.length()").value(6))   // a cadastrada + no máximo 5 novas
                .andExpect(jsonPath("$.sources.title").value("brasilapi"))
                .andExpect(jsonPath("$.sources.language").value("openlibrary"));

        mvc.perform(get("/api/v1/books").param("q", "Livro Importado").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));   // nada foi gravado
    }

    @Test
    void seABrasilApiNaoTemUsaAOpenLibraryMesmoComRedirecionamento() throws Exception {
        lookup(admin, SO_OPEN_LIBRARY).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Livro da Open Library: Sub"))
                .andExpect(jsonPath("$.language").value("Português"))
                .andExpect(jsonPath("$.publicationYear").value(2019))
                .andExpect(jsonPath("$.publisher.name").value("Editora OL"))
                .andExpect(jsonPath("$.coverUrl").value("https://covers.openlibrary.org/b/id/123-L.jpg"))
                .andExpect(jsonPath("$.sources.title").value("openlibrary"));
    }

    @Test
    void provedorLentoEstouraOTempoEABuscaSegueComOOutro() throws Exception {
        long inicio = System.currentTimeMillis();
        lookup(admin, BRASIL_LENTA).andExpect(status().isOk()).andExpect(jsonPath("$.title").value("Só a Open Library"));
        org.junit.jupiter.api.Assertions.assertTrue(System.currentTimeMillis() - inicio < 2800, "esperou a resposta lenta inteira");
    }

    @Test
    void ninguemConheceDa404EProvedoresQuebradosDao503() throws Exception {
        lookup(admin, NINGUEM).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value(containsString("Preencha manualmente")));
        lookup(admin, TUDO_QUEBRADO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(containsString("Preencha manualmente")));
    }

    @Test
    void isbnJaCadastradoAvisaMesmoEmOutroFormatoENaoChamaOsProvedores() throws Exception {
        long id = read(send(admin, "/api/v1/books", """
                {"title":"Akira que já temos","isbn":"85-4570-287-6","copies":[
                  {"barcode":"ISBN-DUP-C","kind":"CONSULTA"},{"barcode":"ISBN-DUP-E","kind":"CIRCULANTE"}]}""")
                .andExpect(status().isCreated())).get("id").asLong();
        int antes = BRASIL_CALLS.get();

        lookup(admin, JA_CADASTRADO).andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyRegistered").value(true))
                .andExpect(jsonPath("$.existingBookId").value(id));

        org.junit.jupiter.api.Assertions.assertEquals(antes, BRASIL_CALLS.get());
    }

    @Test
    void isbnInvalidoDa400() throws Exception {
        for (String ruim : new String[] {"123", "9788535914840", "abcdefghijklm"}) {
            lookup(admin, ruim).andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(containsString("ISBN inválido")));
        }
    }

    @Test
    void soAEquipeConsulta() throws Exception {
        mvc.perform(get("/api/v1/books/isbn/" + AMBOS)).andExpect(status().isUnauthorized());

        int n = SEQ.incrementAndGet();
        String email = "isbn" + n + "@teste.com";
        send(admin, "/api/v1/users", """
                {"fullName":"Leitor %d","email":"%s","password":"%s","roles":["STUDENT"]}""".formatted(n, email, PASSWORD))
                .andExpect(status().isCreated());
        String student = "Bearer " + read(send(login(email, PASSWORD), "/api/v1/auth/password", """
                {"currentPassword":"%s","newPassword":"%s"}""".formatted(PASSWORD, PASSWORD + "-nova"))
                .andExpect(status().isOk())).get("accessToken").asText();
        lookup(student, AMBOS).andExpect(status().isForbidden());
    }
}

package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

/** Senha, sessão, edição, regras e busca contra PostgreSQL real (a busca depende das extensões da V9). Pulado sem Docker. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ContaEdicaoBuscaFluxoTest {

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
    private static final String TEMP = "senha-temporaria-1";
    private static final String FINAL = "senha-definitiva-1";

    @Autowired
    MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();
    private String admin;

    record Person(long id, String email) {
    }

    @BeforeEach
    void setUp() throws Exception {
        admin = login("admin@teste.com", "senha-admin-123");
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String login(String email, String password) throws Exception {
        return "Bearer " + read(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private ResultActions send(String token, String url, String body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions replace(String token, String url, String body) throws Exception {
        return mvc.perform(put(url).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions fetch(String token, String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", token));
    }

    private ResultActions search(String q, String url) throws Exception {
        return mvc.perform(get(url).param("q", q).header("Authorization", admin));
    }

    private Person person(String name, String role) throws Exception {
        String email = "conta" + SEQ.incrementAndGet() + "@teste.com";
        long id = read(send(admin, "/api/v1/users", """
                {"fullName":"%s","email":"%s","password":"%s","roles":["%s"]}""".formatted(name, email, TEMP, role))
                .andExpect(status().isCreated())).get("id").asLong();
        return new Person(id, email);
    }

    /** Entra com a senha temporária, troca e devolve o token já com os perfis. */
    private String activate(Person p) throws Exception {
        return "Bearer " + read(send(login(p.email(), TEMP), "/api/v1/auth/password",
                "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(TEMP, FINAL))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    private JsonNode book(String title, String author) throws Exception {
        int n = SEQ.incrementAndGet();
        long authorId = read(send(admin, "/api/v1/authors", "{\"name\":\"%s\"}".formatted(author))).get("id").asLong();
        return read(send(admin, "/api/v1/books", """
                {"title":"%s","isbn":"ISBN%d","authorIds":[%d],"copies":[{"barcode":"CON-%d","kind":"CONSULTA"},{"barcode":"EMP-%d","kind":"CIRCULANTE"}]}"""
                .formatted(title, n, authorId, n, n)).andExpect(status().isCreated()));
    }

    @Test
    void senhaTemporariaNaoDaPerfilAteSerTrocadaETrocarDerrubaOTokenAntigo() throws Exception {
        Person bib = person("Bia Bibliotecária", "LIBRARIAN");
        String temporary = login(bib.email(), TEMP);
        fetch(temporary, "/api/v1/users").andExpect(status().isForbidden());

        send(temporary, "/api/v1/auth/password", "{\"currentPassword\":\"errada\",\"newPassword\":\"%s\"}".formatted(FINAL))
                .andExpect(status().isUnprocessableEntity());
        send(temporary, "/api/v1/auth/password", "{\"currentPassword\":\"%s\",\"newPassword\":\"%s\"}".formatted(TEMP, TEMP))
                .andExpect(status().isUnprocessableEntity());

        String definitive = activate(bib);
        fetch(definitive, "/api/v1/users").andExpect(status().isOk());
        fetch(temporary, "/api/v1/users").andExpect(status().isUnauthorized());
        login(bib.email(), FINAL);
    }

    @Test
    void desativarDerrubaASessaoNaHoraEReativarDevolveOAcesso() throws Exception {
        Person ana = person("Ana", "STUDENT");
        String token = activate(ana);
        fetch(token, "/api/v1/me/loans").andExpect(status().isOk());

        mvc.perform(delete("/api/v1/users/" + ana.id()).header("Authorization", admin)).andExpect(status().isNoContent());
        fetch(token, "/api/v1/me/loans").andExpect(status().isUnauthorized());

        send(admin, "/api/v1/users/" + ana.id() + "/reactivate", "").andExpect(jsonPath("$.active").value(true));
        fetch(login(ana.email(), FINAL), "/api/v1/me/loans").andExpect(status().isOk());
    }

    @Test
    void administradorRedefineSenhaEODonoEObrigadoATrocar() throws Exception {
        Person ana = person("Ana Reset", "STUDENT");
        String old = activate(ana);

        send(admin, "/api/v1/users/" + ana.id() + "/password", "{\"newPassword\":\"outra-temporaria-1\"}")
                .andExpect(status().isNoContent());

        fetch(old, "/api/v1/me/loans").andExpect(status().isUnauthorized());
        String body = new String(java.util.Base64.getUrlDecoder().decode(login(ana.email(), "outra-temporaria-1").split("\\.")[1]));
        assertEquals(true, json.readTree(body).get("mustChangePassword").asBoolean());
    }

    @Test
    void bibliotecarioNaoMexeEmQuemEstaAcimaDele() throws Exception {
        String librarian = activate(person("Bibi", "LIBRARIAN"));
        Person other = person("Outra Admin", "ADMIN_INSTITUTION");

        send(librarian, "/api/v1/users/" + other.id() + "/password", "{\"newPassword\":\"tomei-a-conta-1\"}")
                .andExpect(status().isUnprocessableEntity());
        replace(librarian, "/api/v1/users/" + other.id(), """
                {"fullName":"X","email":"x@teste.com","roles":["STUDENT"]}""").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void editarUsuarioMudaOsDadosETrocarPerfilDerrubaASessao() throws Exception {
        Person ana = person("Ana Antiga", "STUDENT");
        String token = activate(ana);

        replace(admin, "/api/v1/users/" + ana.id(), """
                {"fullName":"Ana Nova","email":"%s","registration":"R-%d","roles":["STUDENT"]}""".formatted(ana.email(), ana.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Ana Nova"));
        fetch(token, "/api/v1/me/loans").andExpect(status().isOk());   // só dados: a sessão continua

        replace(admin, "/api/v1/users/" + ana.id(), """
                {"fullName":"Ana Nova","email":"%s","roles":["TEACHER"]}""".formatted(ana.email()))
                .andExpect(status().isOk());
        fetch(token, "/api/v1/me/loans").andExpect(status().isUnauthorized());

        replace(admin, "/api/v1/users/" + ana.id(), """
                {"fullName":"Ana Nova","email":"admin@teste.com","roles":["TEACHER"]}""")
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void buscaIgnoraAcentoEProcuraPorAutorIsbnECodigo() throws Exception {
        JsonNode book = book("Memórias do Subsolo Único", "Fiódor Dostoiévski");
        String id = book.get("id").asText();
        String isbn = book.get("isbn").asText();
        String barcode = book.get("copies").get(0).get("barcode").asText();

        for (String q : new String[] {"memorias do subsolo", "UNICO", "dostoievski", isbn, barcode}) {
            search(q, "/api/v1/books")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(Long.valueOf(id)));
        }
        search("%%%", "/api/v1/books").andExpect(jsonPath("$.totalElements").value(0));   // % é texto, não curinga

        Person jose = person("José Antônio Buscado", "STUDENT");
        search("jose antonio", "/api/v1/users").andExpect(jsonPath("$.content[0].id").value(jose.id()));
        search(jose.email(), "/api/v1/users").andExpect(jsonPath("$.totalElements").value(1));
        fetch(admin, "/api/v1/users/lookup?ids=" + jose.id()).andExpect(jsonPath("$[0].fullName").value("José Antônio Buscado"));
    }

    @Test
    void editarObraExemplarEAutorPreservaOsExemplaresERecusaDuplicado() throws Exception {
        JsonNode first = book("Titulo Com Erro", "Autor Com Erro");
        JsonNode second = book("Outra Obra", "Outro Autor");
        long id = first.get("id").asLong();
        long authorId = first.get("authors").get(0).get("id").asLong();
        long copyId = first.get("copies").get(0).get("id").asLong();

        replace(admin, "/api/v1/books/" + id, """
                {"title":"Titulo Correto","publicationYear":1999,"authorIds":[%d]}""".formatted(authorId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titulo Correto"))
                .andExpect(jsonPath("$.copies.length()").value(2));
        replace(admin, "/api/v1/books/" + id, "{\"title\":\"T\",\"isbn\":\"%s\"}".formatted(second.get("isbn").asText()))
                .andExpect(status().isUnprocessableEntity());

        replace(admin, "/api/v1/copies/" + copyId, "{\"barcode\":\"NOVO-%d\",\"condition\":\"RUIM\",\"location\":\"Estante 9\"}".formatted(copyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.condition").value("RUIM"))
                .andExpect(jsonPath("$.kind").value("CONSULTA"));
        replace(admin, "/api/v1/copies/" + copyId, "{\"barcode\":\"%s\"}".formatted(second.get("copies").get(0).get("barcode").asText()))
                .andExpect(status().isUnprocessableEntity());

        replace(admin, "/api/v1/authors/" + authorId, "{\"name\":\"Autor Correto\"}").andExpect(status().isOk());
        fetch(admin, "/api/v1/books/" + id).andExpect(jsonPath("$.authors[0].name").value("Autor Correto"));
    }

    @Test
    void administradorAjustaRegrasMasVisitanteContinuaSemEmprestimoEAlunoNaoAcessa() throws Exception {
        String body = """
                {"maxLoans":5,"loanDays":21,"maxRenewals":1,"renewalDays":7,"reservationPickupDays":2,"dailyRate":1.75}""";
        replace(admin, "/api/v1/policies/STUDENT", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanDays").value(21))
                .andExpect(jsonPath("$.dailyRate").value(1.75));
        fetch(admin, "/api/v1/policies").andExpect(jsonPath("$[?(@.role=='STUDENT')].maxLoans").value(5));

        replace(admin, "/api/v1/policies/VISITOR", body).andExpect(status().isNotFound());
        replace(admin, "/api/v1/policies/STUDENT", body.replace("1.75", "-1")).andExpect(status().isBadRequest());

        String librarian = activate(person("Bibi Regras", "LIBRARIAN"));
        fetch(librarian, "/api/v1/policies").andExpect(status().isForbidden());
    }

    @Test
    void cadaUmVeSeusDadosEAdministracaoAnonimizaSoSemPendenciaESemApagarOHistorico() throws Exception {
        Person ana = person("Ana Privada", "STUDENT");
        send(admin, "/api/v1/auth/login", "{\"email\":\"%s\",\"password\":\"errada-1\"}".formatted(ana.email()))
                .andExpect(status().isUnauthorized());
        String anaToken = activate(ana);

        // direito de acesso: a própria pessoa vê seus dados, inclusive os acessos
        fetch(anaToken, "/api/v1/me/data")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.email").value(ana.email()))
                .andExpect(jsonPath("$.accessLog[0].action").exists());
        fetch(anaToken, "/api/v1/users/" + ana.id() + "/export").andExpect(status().isForbidden());
        fetch(admin, "/api/v1/users/" + ana.id() + "/export")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.full_name").value("Ana Privada"));

        // com empréstimo em aberto não anonimiza
        JsonNode obra = book("Livro da Ana", "Autor Privacidade");
        long copyId = 0;
        for (JsonNode c : obra.get("copies")) {
            if ("CIRCULANTE".equals(c.get("kind").asText())) {
                copyId = c.get("id").asLong();
            }
        }
        long loanId = read(send(admin, "/api/v1/loans", "{\"userId\":%d,\"copyId\":%d}".formatted(ana.id(), copyId))
                .andExpect(status().isCreated())).get("id").asLong();
        send(admin, "/api/v1/users/" + ana.id() + "/anonymize", "").andExpect(status().isUnprocessableEntity());
        send(admin, "/api/v1/loans/" + loanId + "/return", "").andExpect(status().isOk());

        // sem pendência: anonimiza, derruba a sessão, some da busca e libera o e-mail; o histórico fica
        String bib = activate(person("Bibi Privacidade", "LIBRARIAN"));
        send(bib, "/api/v1/users/" + ana.id() + "/anonymize", "").andExpect(status().isForbidden());
        send(admin, "/api/v1/users/" + ana.id() + "/anonymize", "").andExpect(status().isNoContent());
        send(admin, "/api/v1/users/" + ana.id() + "/anonymize", "").andExpect(status().isUnprocessableEntity());
        fetch(anaToken, "/api/v1/me/data").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(ana.email(), FINAL)))
                .andExpect(status().isUnauthorized());
        search("Ana Privada", "/api/v1/users").andExpect(jsonPath("$.totalElements").value(0));
        fetch(admin, "/api/v1/users/" + ana.id() + "/export")
                .andExpect(jsonPath("$.profile.full_name").value("Usuário anonimizado"))
                .andExpect(jsonPath("$.profile.cpf").doesNotExist())
                .andExpect(jsonPath("$.accessLog[?(@.ip_address != null)]").isEmpty())
                .andExpect(jsonPath("$.loans[0].title").value("Livro da Ana"));
    }
}

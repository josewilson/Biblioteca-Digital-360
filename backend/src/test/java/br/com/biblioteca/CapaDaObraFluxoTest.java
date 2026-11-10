package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Capa da obra contra PostgreSQL real: envio, miniatura, leitura pública, troca, remoção e recusas. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class CapaDaObraFluxoTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /** Serviço de capas de mentira: o primeiro dígito depois de 978 escolhe o comportamento. */
    static final HttpServer COVERS = startCoverServer();

    private static byte[] pngBytes() {
        try {
            return image("png", 400, 600);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static HttpServer startCoverServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/cover/", exchange -> {
                String isbn = exchange.getRequestURI().getPath().substring("/cover/".length());
                switch (isbn.substring(3, 4)) {
                    case "1" -> {   // tem capa
                        byte[] png = pngBytes();
                        exchange.getResponseHeaders().add("Content-Type", "image/png");
                        exchange.sendResponseHeaders(200, png.length);
                        exchange.getResponseBody().write(png);
                    }
                    case "2" -> exchange.sendResponseHeaders(404, -1);   // sem capa
                    case "3" -> {   // redireciona para fora dos servidores confiáveis
                        exchange.getResponseHeaders().add("Location", "http://servidor-estranho.example/capa.png");
                        exchange.sendResponseHeaders(302, -1);
                    }
                    case "4" -> {   // redireciona para o mesmo servidor (como o Open Library faz)
                        exchange.getResponseHeaders().add("Location", "/cover/9781000000000");
                        exchange.sendResponseHeaders(302, -1);
                    }
                    case "5" -> exchange.sendResponseHeaders(500, -1);
                    default -> exchange.sendResponseHeaders(400, -1);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("app.covers.lookup-url", () -> "http://localhost:" + COVERS.getAddress().getPort() + "/cover/%s");
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", POSTGRES::getUsername);
        r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.jwt.secret", () -> "segredo-de-teste-com-mais-de-32-caracteres");
        r.add("app.admin.email", () -> "admin@teste.com");
        r.add("app.admin.password", () -> "senha-admin-123");
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

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

    private long newBook() throws Exception {
        int n = SEQ.incrementAndGet();
        long author = read(mvc.perform(post("/api/v1/authors").header("Authorization", admin)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Autor da capa %d\"}".formatted(n)))).get("id").asLong();
        return read(mvc.perform(post("/api/v1/books").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Livro com capa %d","authorIds":[%d],"copies":[
                          {"barcode":"CAPA-%d-C","kind":"CONSULTA"},{"barcode":"CAPA-%d-E","kind":"CIRCULANTE"}]}"""
                        .formatted(n, author, n, n))).andExpect(status().isCreated())).get("id").asLong();
    }

    private static byte[] image(String format, int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                img.setRGB(x, y, 0xFF3366CC);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(format.equals("jpg") ? toRgb(img) : img, format, out);
        return out.toByteArray();
    }

    private static BufferedImage toRgb(BufferedImage src) {
        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(src, 0, 0, null);
        return rgb;
    }

    private ResultActions upload(String token, long bookId, String name, String type, byte[] bytes) throws Exception {
        var request = multipart(HttpMethod.PUT, "/api/v1/books/" + bookId + "/cover")
                .file(new MockMultipartFile("file", name, type, bytes));
        return mvc.perform(token == null ? request : request.header("Authorization", token));
    }

    private BufferedImage decode(byte[] jpeg) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(jpeg));
    }

    @Test
    void enviaPngGeraCapaEMiniaturaJpegELeituraNaoPedeLogin() throws Exception {
        long id = newBook();
        upload(admin, id, "capa.png", "image/png", image("png", 1200, 1800))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coverVersion").isNumber());

        byte[] full = mvc.perform(get("/api/v1/books/" + id + "/cover")).andExpect(status().isOk())   // sem token: <img> não envia
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Type", "image/jpeg"))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] thumb = mvc.perform(get("/api/v1/books/" + id + "/cover").param("size", "thumb")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertEquals(0xFF, full[0] & 0xFF);   // JPEG de verdade (regravado), não o PNG enviado
        assertEquals(0xD8, full[1] & 0xFF);
        BufferedImage fullImage = decode(full);
        BufferedImage thumbImage = decode(thumb);
        assertEquals(600, fullImage.getWidth());
        assertEquals(900, fullImage.getHeight());
        assertEquals(160, thumbImage.getWidth());
        assertEquals(240, thumbImage.getHeight());
        assertTrue(thumb.length < full.length);
    }

    @Test
    void naoAmpliaImagemPequenaETrocaDeCapaMudaAVersao() throws Exception {
        long id = newBook();
        long v1 = read(upload(admin, id, "a.jpg", "image/jpeg", image("jpg", 100, 150)).andExpect(status().isOk())).get("coverVersion").asLong();
        Thread.sleep(5);
        long v2 = read(upload(admin, id, "b.png", "image/png", image("png", 50, 50)).andExpect(status().isOk())).get("coverVersion").asLong();

        assertTrue(v2 > v1);
        BufferedImage now = decode(mvc.perform(get("/api/v1/books/" + id + "/cover")).andReturn().getResponse().getContentAsByteArray());
        assertEquals(50, now.getWidth());   // a nova substituiu a antiga, sem ampliar
    }

    @Test
    void removerApagaACapa() throws Exception {
        long id = newBook();
        upload(admin, id, "capa.png", "image/png", image("png", 300, 400)).andExpect(status().isOk());

        mvc.perform(delete("/api/v1/books/" + id + "/cover").header("Authorization", admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.coverVersion").doesNotExist());

        mvc.perform(get("/api/v1/books/" + id + "/cover")).andExpect(status().isNotFound());
    }

    @Test
    void recusaArquivoQueNaoEImagemJpegOuPngEImagemGrandeDemais() throws Exception {
        long id = newBook();
        upload(admin, id, "x.png", "image/png", "<script>alert(1)</script>".getBytes()).andExpect(status().isUnprocessableEntity());
        upload(admin, id, "x.gif", "image/gif", image("gif", 20, 20)).andExpect(status().isUnprocessableEntity());   // formato fora da lista
        upload(admin, id, "enorme.png", "image/png", image("png", 9000, 2)).andExpect(status().isUnprocessableEntity());   // pouco byte, muito pixel
        upload(admin, id, "grande.png", "image/png", new byte[2 * 1024 * 1024 + 1]).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/books/" + id + "/cover")).andExpect(status().isNotFound());   // nada ficou gravado
    }

    private long newBookWithIsbn(String isbnPrefix) throws Exception {
        int n = SEQ.incrementAndGet();
        String isbn = isbnPrefix + "%09d".formatted(n);
        return read(mvc.perform(post("/api/v1/books").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"Livro com ISBN %d","isbn":"%s","copies":[
                          {"barcode":"ISBN-%d-C","kind":"CONSULTA"},{"barcode":"ISBN-%d-E","kind":"CIRCULANTE"}]}"""
                        .formatted(n, isbn, n, n))).andExpect(status().isCreated())).get("id").asLong();
    }

    private ResultActions lookup(long id) throws Exception {
        return mvc.perform(post("/api/v1/books/" + id + "/cover/lookup").header("Authorization", admin));
    }

    @Test
    void buscaACapaPeloIsbnERegravaComoJpeg() throws Exception {
        long id = newBookWithIsbn("9781");

        lookup(id).andExpect(status().isOk()).andExpect(jsonPath("$.coverVersion").isNumber());

        byte[] thumb = mvc.perform(get("/api/v1/books/" + id + "/cover").param("size", "thumb")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals(0xFF, thumb[0] & 0xFF);   // JPEG regravado, não o PNG do serviço
        assertEquals(160, decode(thumb).getWidth());
    }

    @Test
    void seguePeloMesmoServidorMasNaoParaForaDosConfiaveis() throws Exception {
        lookup(newBookWithIsbn("9784")).andExpect(status().isOk());   // redirecionou para o mesmo servidor
        lookup(newBookWithIsbn("9783")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Não foi possível consultar")));
    }

    @Test
    void semCapaNoServicoOuServicoComErroDaMensagemClaraENadaFicaGravado() throws Exception {
        long semCapa = newBookWithIsbn("9782");
        lookup(semCapa).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Nenhuma capa encontrada")));
        mvc.perform(get("/api/v1/books/" + semCapa + "/cover")).andExpect(status().isNotFound());

        lookup(newBookWithIsbn("9785")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Não foi possível consultar")));
    }

    @Test
    void exigeIsbnValidoSalvoNaObraEFicaRestritaAEquipe() throws Exception {
        lookup(newBook()).andExpect(status().isUnprocessableEntity())   // obra sem ISBN
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("Informe o ISBN")));
        long curto = read(mvc.perform(post("/api/v1/books").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title":"ISBN curto","isbn":"123","copies":[{"barcode":"CURTO-C","kind":"CONSULTA"},{"barcode":"CURTO-E","kind":"CIRCULANTE"}]}"""))
                .andExpect(status().isCreated())).get("id").asLong();
        lookup(curto).andExpect(status().isUnprocessableEntity());

        mvc.perform(post("/api/v1/books/" + curto + "/cover/lookup")).andExpect(status().isUnauthorized());
    }

    @Test
    void soAEquipeEnviaOuRemove() throws Exception {
        long id = newBook();
        String email = "leitor" + SEQ.incrementAndGet() + "@teste.com";
        mvc.perform(post("/api/v1/users").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"Leitor","email":"%s","password":"senha-temp-123","roles":["STUDENT"]}""".formatted(email)))
                .andExpect(status().isCreated());
        // a senha temporária tira os perfis do token: troca antes, como o leitor faria
        String temp = login(email, "senha-temp-123");
        String reader = "Bearer " + read(mvc.perform(post("/api/v1/auth/password").header("Authorization", temp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"senha-temp-123\",\"newPassword\":\"senha-leitor-123\"}")).andExpect(status().isOk()))
                .get("accessToken").asText();

        upload(reader, id, "capa.png", "image/png", image("png", 10, 10)).andExpect(status().isForbidden());
        upload(null, id, "capa.png", "image/png", image("png", 10, 10)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/books/" + id + "/cover").header("Authorization", reader)).andExpect(status().isForbidden());
    }
}

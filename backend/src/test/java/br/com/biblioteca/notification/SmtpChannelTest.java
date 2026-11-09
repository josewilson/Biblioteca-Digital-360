package br.com.biblioteca.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * O canal SMTP contra um servidor SMTP de verdade (Mailpit, em container), com o {@link JavaMailSender} montado pela
 * mesma autoconfiguração e pelas mesmas propriedades {@code spring.mail.*} da aplicação. Pulado sem Docker.
 * Prova o caminho (conexão, codificação dos acentos, destinatário); não prova o seu provedor (TLS, autenticação).
 */
@Testcontainers(disabledWithoutDocker = true)
class SmtpChannelTest {

    @Container
    static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit:v1.33.1").withExposedPorts(1025, 8025);

    private final ObjectMapper json = new ObjectMapper();

    private ApplicationContextRunner runner(int smtpPort) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
                .withPropertyValues("spring.mail.host=" + MAILPIT.getHost(), "spring.mail.port=" + smtpPort);
    }

    private JsonNode inbox() throws Exception {
        HttpResponse<String> res = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://%s:%d/api/v1/messages".formatted(MAILPIT.getHost(), MAILPIT.getMappedPort(8025)))).build(),
                HttpResponse.BodyHandlers.ofString());
        return json.readTree(res.body());
    }

    @Test
    void entregaOAvisoComAcentosAoServidorSmtp() throws Exception {
        runner(MAILPIT.getMappedPort(1025)).run(ctx -> {
            SmtpChannel channel = new SmtpChannel(ctx.getBean(JavaMailSender.class), "biblioteca@escola.test", MAILPIT.getHost(), "", false);

            channel.send("ana@leitor.test", "Empréstimo confirmado", "Devolução até 20/11/2026: \"Dom Casmurro\" — não esqueça.");
        });

        JsonNode message = inbox().get("messages").get(0);
        assertEquals("ana@leitor.test", message.get("To").get(0).get("Address").asText());
        assertEquals("biblioteca@escola.test", message.get("From").get("Address").asText());
        assertEquals("Empréstimo confirmado", message.get("Subject").asText());
        assertTrue(message.get("Snippet").asText().contains("não esqueça"), message.get("Snippet").asText());
    }

    @Test
    void recusaSubirSemServidorOuRemetenteOuSemLoginQuandoAutenticacaoEstaLigada() {
        JavaMailSender sender = org.mockito.Mockito.mock(JavaMailSender.class);

        assertThrows(IllegalStateException.class, () -> new SmtpChannel(sender, "biblioteca@escola.test", "", "", false));
        assertThrows(IllegalStateException.class, () -> new SmtpChannel(sender, "", "smtp.provedor.test", "", false));
        assertThrows(IllegalStateException.class, () -> new SmtpChannel(sender, "biblioteca@escola.test", "smtp.provedor.test", "", true));
        new SmtpChannel(sender, "biblioteca@escola.test", "smtp.provedor.test", "usuario", true);   // completo: sobe
        new SmtpChannel(sender, "biblioteca@escola.test", "localhost", "", false);                   // servidor local sem senha
    }

    @Test
    void servidorForaDoArFalhaComExcecaoParaOAvisoFicarPendente() {
        // porta sem ninguém ouvindo: o despachante conta a tentativa e tenta de novo (NotificationDispatcher)
        runner(1).run(ctx -> {
            SmtpChannel channel = new SmtpChannel(ctx.getBean(JavaMailSender.class), "biblioteca@escola.test", MAILPIT.getHost(), "", false);
            assertThrows(MailException.class, () -> channel.send("ana@leitor.test", "Assunto", "Corpo"));
        });
    }
}

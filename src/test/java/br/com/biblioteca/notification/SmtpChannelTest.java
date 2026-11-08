package br.com.biblioteca.notification;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class SmtpChannelTest {

    @Test
    void montaEEnviaAMensagemComRemetenteConfigurado() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new SmtpChannel(sender, "biblioteca@escola.edu").send("ana@x.com", "Multa registrada", "Multa de R$ 3,00.");

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(sent.capture());
        assertEquals("biblioteca@escola.edu", sent.getValue().getFrom());
        assertArrayEquals(new String[] {"ana@x.com"}, sent.getValue().getTo());
        assertEquals("Multa registrada", sent.getValue().getSubject());
        assertEquals("Multa de R$ 3,00.", sent.getValue().getText());
    }

    @Test
    void canalDeLogMascaraOEmail() {
        assertEquals("a***@x.com", LoggingChannel.mask("ana@x.com"));
        assertEquals("***", LoggingChannel.mask("a@x.com"));
    }
}

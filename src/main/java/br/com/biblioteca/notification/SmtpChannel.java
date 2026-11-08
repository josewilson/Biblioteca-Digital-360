package br.com.biblioteca.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio real por SMTP. Ative com {@code app.notifications.channel=smtp}, {@code app.notifications.from}
 * e as propriedades {@code spring.mail.*} (host, porta, usuário, senha: sempre por variável de ambiente).
 */
@Component
@ConditionalOnProperty(name = "app.notifications.channel", havingValue = "smtp")
class SmtpChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final String from;

    SmtpChannel(JavaMailSender mailSender, @Value("${app.notifications.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public void send(String toEmail, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}

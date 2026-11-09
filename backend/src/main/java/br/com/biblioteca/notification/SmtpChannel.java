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

    SmtpChannel(JavaMailSender mailSender,
                @Value("${app.notifications.from:}") String from,
                @Value("${spring.mail.host:}") String host,
                @Value("${spring.mail.username:}") String username,
                @Value("${app.notifications.smtp.auth:true}") boolean auth) {
        if (host.isBlank() || from.isBlank()) {
            throw new IllegalStateException(
                    "Canal smtp ligado, mas faltam SPRING_MAIL_HOST e APP_NOTIFICATIONS_FROM. Preencha-os ou use APP_NOTIFICATIONS_CHANNEL=log.");
        }
        if (auth && username.isBlank()) {
            throw new IllegalStateException(
                    "Canal smtp com autenticação ligada, mas sem SPRING_MAIL_USERNAME/SPRING_MAIL_PASSWORD. "
                            + "Preencha-os ou, num servidor sem senha, use MAIL_SMTP_AUTH=false.");
        }
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

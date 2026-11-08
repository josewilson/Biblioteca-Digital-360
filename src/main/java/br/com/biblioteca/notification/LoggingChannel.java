package br.com.biblioteca.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Canal padrão: não envia nada, só registra no log (com o e-mail mascarado, por LGPD). Bom para desenvolvimento. */
@Component
@ConditionalOnProperty(name = "app.notifications.channel", havingValue = "log", matchIfMissing = true)
class LoggingChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LoggingChannel.class);

    @Override
    public void send(String toEmail, String subject, String body) {
        log.info("[notificação simulada] para {}: {}", mask(toEmail), subject);
    }

    static String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }
}

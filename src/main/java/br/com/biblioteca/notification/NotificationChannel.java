package br.com.biblioteca.notification;

/**
 * Canal de entrega dos avisos (e-mail hoje; SMS ou push no futuro seria outra implementação).
 * Escolhido por configuração: {@code app.notifications.channel=log|smtp}.
 */
public interface NotificationChannel {

    /** @throws RuntimeException se não conseguir entregar; o aviso fica pendente e é tentado de novo */
    void send(String toEmail, String subject, String body);
}

package br.com.biblioteca.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.notification.NotificationService;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.user.User;
import br.com.biblioteca.user.UserRepository;

/**
 * "Esqueci a senha": o leitor pede um link por e-mail e define a senha nova por ele.
 * O token (256 bits aleatórios) só existe no e-mail; o banco guarda o hash. Vale 1 hora e uma vez só.
 * O pedido nunca revela se o e-mail existe (mesma resposta sempre).
 */
@Service
public class PasswordResetService {

    static final Duration TTL = Duration.ofHours(1);
    /** Pedidos por conta por hora: impede usar o sistema para lotar a caixa de entrada de alguém. */
    static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final NotificationService notifications;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String publicUrl;

    PasswordResetService(UserRepository users, PasswordEncoder encoder, NotificationService notifications,
                         AuditService audit, JdbcTemplate jdbc, Clock clock,
                         @Value("${app.public-url}") String publicUrl) {
        this.users = users;
        this.encoder = encoder;
        this.notifications = notifications;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Transactional
    public void request(String rawEmail, String ip) {
        users.findByEmailIgnoreCase(rawEmail.trim().toLowerCase()).filter(User::canLogin).ifPresent(user -> {
            Instant now = clock.instant();
            Integer recent = jdbc.queryForObject(
                    "select count(*) from password_resets where user_id = ? and created_at > ?",
                    Integer.class, user.getId(), Timestamp.from(now.minus(Duration.ofHours(1))));
            if (recent != null && recent >= MAX_REQUESTS_PER_HOUR) {
                return;
            }
            byte[] bytes = new byte[32];
            RANDOM.nextBytes(bytes);
            String token = HexFormat.of().formatHex(bytes);
            jdbc.update("update password_resets set used_at = ? where user_id = ? and used_at is null",
                    Timestamp.from(now), user.getId());   // só o link mais novo vale
            jdbc.update("insert into password_resets (user_id, token_hash, expires_at, created_at) values (?, ?, ?, ?)",
                    user.getId(), hash(token), Timestamp.from(now.plus(TTL)), Timestamp.from(now));
            notifications.record(user.getId(), "PASSWORD_RESET",
                    "Você pediu para redefinir a senha. Abra o link em até " + TTL.toMinutes() + " minutos: "
                            + publicUrl + "/redefinir-senha?token=" + token
                            + " Se não foi você, ignore esta mensagem: a senha atual continua valendo.");
            audit.record(user.getId(), "PASSWORD_RESET_REQUESTED", "user", user.getId(), null, ip);
        });
    }

    @Transactional
    public void reset(String token, String newPassword, String ip) {
        Instant now = clock.instant();
        // Uma instrução só: dois usos simultâneos do mesmo link não passam os dois.
        List<Long> claimed = jdbc.query(
                "update password_resets set used_at = ? where token_hash = ? and used_at is null and expires_at > ? returning user_id",
                (rs, i) -> rs.getLong(1), Timestamp.from(now), hash(token.trim()), Timestamp.from(now));
        User user = claimed.isEmpty() ? null : users.findById(claimed.get(0)).filter(User::canLogin).orElse(null);
        if (user == null) {
            throw new BusinessRuleException("Link inválido ou expirado. Peça um novo na tela de entrada.");
        }
        user.changePassword(encoder.encode(newPassword), false);   // também derruba as sessões abertas
        jdbc.update("update password_resets set used_at = ? where user_id = ? and used_at is null",
                Timestamp.from(now), user.getId());
        audit.record(user.getId(), "PASSWORD_RESET_DONE", "user", user.getId(), null, ip);
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

package br.com.biblioteca.security;

import java.time.Clock;
import java.time.Duration;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.error.TooManyAttemptsException;
import br.com.biblioteca.user.User;
import br.com.biblioteca.user.UserRepository;

@Service
public class AuthService {

    /** Após tantas falhas seguidas para o mesmo e-mail, o login fica bloqueado pela janela abaixo. */
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT_WINDOW = Duration.ofMinutes(15);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final Clock clock;
    /** Hash de uma senha que ninguém tem: usado quando o e-mail não existe, para gastar o mesmo tempo de uma conferência real. */
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.clock = clock;
        this.dummyHash = encoder.encode("senha-que-nenhum-usuario-possui");
    }

    // Sem @Transactional de propósito: o registro da falha precisa persistir mesmo com a exceção.
    public TokenResponse login(LoginRequest request, String ip) {
        String email = request.email().trim().toLowerCase();

        // Bloqueio por força bruta. Tentativas bloqueadas NÃO contam como falha (senão o bloqueio nunca terminaria).
        // Limite conhecido: o bloqueio é por e-mail, então alguém pode trancar a conta de outra pessoa por 15 min;
        // a troca é aceita porque a alternativa (livre tentativa de senhas) é pior.
        if (audit.failedLoginsSince(email, clock.instant().minus(LOCKOUT_WINDOW)) >= MAX_FAILED_ATTEMPTS) {
            audit.record(null, "LOGIN_BLOCKED", "user", null, email, ip);
            throw new TooManyAttemptsException(
                    "Muitas tentativas de login. Tente novamente em " + LOCKOUT_WINDOW.toMinutes() + " minutos.");
        }

        User user = users.findByEmailIgnoreCase(email).filter(User::canLogin).orElse(null);
        // Confere sempre uma senha (mesmo sem usuário) para o tempo de resposta não revelar se o e-mail existe.
        String hash = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordOk = encoder.matches(request.password(), hash);

        if (user == null || !passwordOk) {
            audit.record(null, "LOGIN_FAILED", "user", null, email, ip);
            throw new BadCredentialsException("Credenciais inválidas.");
        }
        audit.record(user.getId(), "LOGIN_OK", "user", user.getId(), null, ip);
        return jwt.issue(user);
    }
}

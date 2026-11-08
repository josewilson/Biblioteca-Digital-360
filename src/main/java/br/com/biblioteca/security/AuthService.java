package br.com.biblioteca.security;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.user.User;
import br.com.biblioteca.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
    }

    // Sem @Transactional de propósito: o registro da falha precisa persistir mesmo com a exceção.
    public TokenResponse login(LoginRequest request, String ip) {
        String email = request.email().trim().toLowerCase();
        // ponytail: usuário inexistente responde mais rápido que senha errada (sem BCrypt); equalizar se houver risco de enumeração.
        User user = users.findByEmailIgnoreCase(email).filter(User::canLogin).orElse(null);
        if (user == null || !encoder.matches(request.password(), user.getPasswordHash())) {
            audit.record(null, "LOGIN_FAILED", "user", null, email, ip);
            throw new BadCredentialsException("Credenciais inválidas.");
        }
        audit.record(user.getId(), "LOGIN_OK", "user", user.getId(), null, ip);
        return jwt.issue(user);
    }
}

package br.com.biblioteca.security;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import br.com.biblioteca.user.UserRepository;

/**
 * Além da assinatura e da validade, confere no banco se a sessão ainda vale: usuário desativado,
 * senha trocada ou perfis alterados derrubam os tokens já emitidos na hora (HTTP 401).
 * Custo: uma consulta por chave primária a cada requisição.
 */
@Component
@Primary
class SessionAwareJwtDecoder implements JwtDecoder {

    private final JwtDecoder signed;
    private final UserRepository users;

    SessionAwareJwtDecoder(@Qualifier("jwtDecoder") JwtDecoder signed, UserRepository users) {
        this.signed = signed;
        this.users = users;
    }

    @Override
    public Jwt decode(String token) {
        Jwt jwt = signed.decode(token);
        Object version = jwt.getClaim(JwtService.CLAIM_VERSION);
        boolean valid = version instanceof Number n
                && users.findActiveTokenVersion(Long.valueOf(jwt.getSubject()))
                        .filter(current -> current == n.intValue()).isPresent();
        if (!valid) {
            throw new BadJwtException("Sessão encerrada.");
        }
        return jwt;
    }
}

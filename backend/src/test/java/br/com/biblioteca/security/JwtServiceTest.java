package br.com.biblioteca.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import br.com.biblioteca.user.Role;
import br.com.biblioteca.user.User;

class JwtServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres";
    private final SecurityConfig config = new SecurityConfig(SECRET);

    @Test
    void tokenEmitidoEhAceitoEGuardaIdEPerfis() {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn("LIBRARIAN");
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getRoles()).thenReturn(Set.of(role));

        TokenResponse token = new JwtService(config.jwtEncoder(), 60).issue(user);
        Jwt jwt = config.jwtDecoder().decode(token.accessToken());

        assertEquals("7", jwt.getSubject());
        assertEquals(List.of("LIBRARIAN"), jwt.getClaimAsStringList("roles"));
    }

    @Test
    void tokenAdulteradoOuDeOutraChaveEhRecusado() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.getRoles()).thenReturn(Set.of());
        String token = new JwtService(config.jwtEncoder(), 60).issue(user).accessToken();

        JwtDecoder outraChave = new SecurityConfig("outro-segredo-totalmente-diferente-123456").jwtDecoder();
        assertThrows(JwtException.class, () -> outraChave.decode(token));
    }

    @Test
    void segredoCurtoNaoSobe() {
        assertThrows(IllegalStateException.class, () -> new SecurityConfig("curto"));
    }
}

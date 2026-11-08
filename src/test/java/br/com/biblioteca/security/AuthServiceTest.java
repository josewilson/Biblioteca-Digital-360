package br.com.biblioteca.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.error.TooManyAttemptsException;
import br.com.biblioteca.user.User;
import br.com.biblioteca.user.UserRepository;

class AuthServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-02T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuditService audit = mock(AuditService.class);
    private AuthService service;
    private User ana;

    @BeforeEach
    void setUp() {
        when(encoder.encode(any())).thenReturn("HASH-FALSO");
        service = new AuthService(users, encoder, jwt, audit, clock);
        ana = mock(User.class);
        when(ana.canLogin()).thenReturn(true);
        when(ana.getId()).thenReturn(7L);
        when(ana.getPasswordHash()).thenReturn("HASH-DA-ANA");
        when(users.findByEmailIgnoreCase("ana@x.com")).thenReturn(Optional.of(ana));
    }

    @Test
    void senhaCertaEmiteOToken() {
        when(encoder.matches("certa", "HASH-DA-ANA")).thenReturn(true);
        TokenResponse token = new TokenResponse("jwt", "Bearer", 3600);
        when(jwt.issue(ana)).thenReturn(token);

        assertEquals(token, service.login(new LoginRequest(" Ana@X.com ", "certa"), "ip"));
        verify(audit).record(7L, "LOGIN_OK", "user", 7L, null, "ip");
    }

    @Test
    void senhaErradaRegistraFalhaEResponde401() {
        when(encoder.matches("errada", "HASH-DA-ANA")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest("ana@x.com", "errada"), "ip"));
        verify(audit).record(null, "LOGIN_FAILED", "user", null, "ana@x.com", "ip");
    }

    @Test
    void emailInexistenteTambemConfereUmaSenhaParaNaoRevelarPeloTempo() {
        assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest("ninguem@x.com", "qualquer"), "ip"));

        verify(encoder).matches("qualquer", "HASH-FALSO");   // o hash de mentira, gerado na criação do serviço
        verify(audit).record(null, "LOGIN_FAILED", "user", null, "ninguem@x.com", "ip");
    }

    @Test
    void usuarioDesativadoNaoEntraMesmoComSenhaCerta() {
        when(ana.canLogin()).thenReturn(false);
        when(encoder.matches("certa", "HASH-FALSO")).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> service.login(new LoginRequest("ana@x.com", "certa"), "ip"));
        verify(jwt, never()).issue(any());
    }

    @Test
    void quatroFalhasAindaPermitemTentar() {
        when(audit.failedLoginsSince(eq("ana@x.com"), any())).thenReturn(4L);
        when(encoder.matches("certa", "HASH-DA-ANA")).thenReturn(true);
        when(jwt.issue(ana)).thenReturn(new TokenResponse("jwt", "Bearer", 3600));

        assertEquals("jwt", service.login(new LoginRequest("ana@x.com", "certa"), "ip").accessToken());
    }

    @Test
    void cincoFalhasBloqueiamMesmoComSenhaCertaESemContarComoNovaFalha() {
        when(audit.failedLoginsSince(eq("ana@x.com"), any())).thenReturn(5L);

        assertThrows(TooManyAttemptsException.class, () -> service.login(new LoginRequest("ana@x.com", "certa"), "ip"));

        verify(encoder, never()).matches(any(), any());          // nem confere a senha
        verify(jwt, never()).issue(any());
        verify(audit).record(null, "LOGIN_BLOCKED", "user", null, "ana@x.com", "ip");
        verify(audit, never()).record(any(), eq("LOGIN_FAILED"), any(), any(), any(), any());   // bloqueio não prolonga o bloqueio
    }

    @Test
    void ajanelaDeContagemSaoOsUltimos15Minutos() {
        when(encoder.matches(any(), any())).thenReturn(true);
        when(jwt.issue(ana)).thenReturn(new TokenResponse("jwt", "Bearer", 3600));

        service.login(new LoginRequest("ana@x.com", "certa"), "ip");

        ArgumentCaptor<Instant> since = ArgumentCaptor.forClass(Instant.class);
        verify(audit).failedLoginsSince(eq("ana@x.com"), since.capture());
        assertEquals(clock.instant().minusSeconds(15 * 60), since.getValue());
    }
}

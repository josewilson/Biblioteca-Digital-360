package br.com.biblioteca.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.biblioteca.acervo.Book;
import br.com.biblioteca.acervo.BookCopy;
import br.com.biblioteca.acervo.BookCopyRepository;
import br.com.biblioteca.acervo.BookRepository;
import br.com.biblioteca.acervo.CopyKind;
import br.com.biblioteca.acervo.CopyStatus;
import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.notification.NotificationService;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;
import br.com.biblioteca.user.User;
import br.com.biblioteca.user.UserRepository;

class ReservationServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-02T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final Instant now = clock.instant();

    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final BookCopyRepository copies = mock(BookCopyRepository.class);
    private final BookRepository books = mock(BookRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BorrowerPolicies policies = mock(BorrowerPolicies.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ReservationService service = new ReservationService(reservations, copies, books, users, policies,
            notifications, mock(AuditService.class), clock);

    private final Book book = new Book("Dom Casmurro");
    private User user;

    @BeforeEach
    void setUp() {
        user = mock(User.class);
        when(user.getId()).thenReturn(1L);
        when(user.isActive()).thenReturn(true);
        when(users.findForUpdateById(1L)).thenReturn(Optional.of(user));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        when(books.findById(2L)).thenReturn(Optional.of(book));
        when(policies.policyFor(any())).thenReturn(new LoanPolicy(3, 14, 2, 7, 3));
        when(reservations.save(any(Reservation.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BookCopy circulatingCopy() {
        BookCopy c = spy(new BookCopy("E-1", CopyKind.CIRCULANTE, null, null));
        doReturn(10L).when(c).getId();
        return c;
    }

    private Reservation waiting() {
        return new Reservation(book, 1L, now);
    }

    // ---------- reservar ----------

    @Test
    void reservaEEntraNaFilaNaPrimeiraPosicao() {
        ReservationResponse r = service.reserve(1L, 2L, 1L, "ip");

        assertEquals(ReservationStatus.NA_FILA, r.status());
        assertEquals(1, r.queuePosition());
    }

    @Test
    void recusaReservarQuandoHaExemplarDisponivel() {
        when(copies.existsByBookIdAndKindAndStatus(any(), eq(CopyKind.CIRCULANTE), eq(CopyStatus.DISPONIVEL)))
                .thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> service.reserve(1L, 2L, 1L, "ip"));
    }

    @Test
    void recusaReservaDuplicadaDoMesmoLeitor() {
        when(reservations.existsByUserIdAndBookIdAndStatusIn(any(), any(), anyCollection())).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> service.reserve(1L, 2L, 1L, "ip"));
    }

    @Test
    void recusaReservaDePerfilQueNaoEmprestaComoVisitante() {
        when(policies.policyFor(any())).thenThrow(new BusinessRuleException("sem política"));
        assertThrows(BusinessRuleException.class, () -> service.reserve(1L, 2L, 1L, "ip"));
        verify(reservations, never()).save(any());
    }

    // ---------- fila ----------

    @Test
    void exemplarLivreFicaGuardadoParaOPrimeiroDaFilaComPrazoENotificacao() {
        Reservation first = waiting();
        BookCopy copy = circulatingCopy();
        when(reservations.findFirstByBookIdAndStatusOrderByIdAsc(any(), eq(ReservationStatus.NA_FILA)))
                .thenReturn(Optional.of(first), Optional.empty());
        when(copies.findFirstByBookIdAndKindAndStatusOrderByIdAsc(any(), any(), any())).thenReturn(Optional.of(copy));

        service.serveQueue(2L);

        assertEquals(CopyStatus.RESERVADO, copy.getStatus());
        assertEquals(ReservationStatus.DISPONIVEL, first.getStatus());
        assertEquals(now.plus(Duration.ofDays(3)), first.getExpiresAt());
        verify(notifications).record(eq(1L), eq("RESERVATION_AVAILABLE"), any());
    }

    @Test
    void semExemplarLivreAReservaContinuaNaFila() {
        Reservation first = waiting();
        when(reservations.findFirstByBookIdAndStatusOrderByIdAsc(any(), any())).thenReturn(Optional.of(first));
        when(copies.findFirstByBookIdAndKindAndStatusOrderByIdAsc(any(), any(), any())).thenReturn(Optional.empty());

        service.serveQueue(2L);

        assertEquals(ReservationStatus.NA_FILA, first.getStatus());
    }

    @Test
    void leitorQueNaoPodeMaisEmprestarSaiDaFilaSemTravarAProxima() {
        Reservation first = waiting();
        BookCopy copy = circulatingCopy();
        when(reservations.findFirstByBookIdAndStatusOrderByIdAsc(any(), any()))
                .thenReturn(Optional.of(first), Optional.empty());
        when(copies.findFirstByBookIdAndKindAndStatusOrderByIdAsc(any(), any(), any())).thenReturn(Optional.of(copy));
        when(policies.policyFor(any())).thenThrow(new BusinessRuleException("perdeu o perfil"));

        service.serveQueue(2L);

        assertEquals(ReservationStatus.CANCELADA, first.getStatus());
        assertEquals(CopyStatus.DISPONIVEL, copy.getStatus());
    }

    // ---------- retirada ----------

    private Reservation heldFor(Long userId, Instant until, BookCopy copy) {
        Reservation r = new Reservation(book, userId, now.minus(Duration.ofDays(5)));
        copy.hold();
        r.holdCopy(copy, until);
        when(reservations.findByCopyIdAndStatus(10L, ReservationStatus.DISPONIVEL)).thenReturn(Optional.of(r));
        return r;
    }

    @Test
    void donoDaReservaRetiraDentroDoPrazo() {
        BookCopy copy = circulatingCopy();
        Reservation r = heldFor(1L, now.plus(Duration.ofDays(1)), copy);

        service.claimHeldCopy(copy, 1L);

        assertEquals(ReservationStatus.RETIRADA, r.getStatus());
        assertEquals(CopyStatus.DISPONIVEL, copy.getStatus());   // pronto para o copy.lend() do empréstimo
    }

    @Test
    void outroLeitorNaoRetiraExemplarReservado() {
        BookCopy copy = circulatingCopy();
        heldFor(1L, now.plus(Duration.ofDays(1)), copy);

        assertThrows(BusinessRuleException.class, () -> service.claimHeldCopy(copy, 99L));
        assertEquals(CopyStatus.RESERVADO, copy.getStatus());
    }

    @Test
    void naoRetiraDepoisDoPrazo() {
        BookCopy copy = circulatingCopy();
        heldFor(1L, now.minus(Duration.ofHours(1)), copy);

        assertThrows(BusinessRuleException.class, () -> service.claimHeldCopy(copy, 1L));
    }

    // ---------- cancelar e expirar ----------

    @Test
    void leitorNaoCancelaReservaDeOutro() {
        Reservation r = waiting();   // do leitor 1
        when(reservations.findForUpdateById(7L)).thenReturn(Optional.of(r));

        assertThrows(ResourceNotFoundException.class, () -> service.cancel(7L, 99L, false, "ip"));
        assertEquals(ReservationStatus.NA_FILA, r.getStatus());
    }

    @Test
    void cancelarReservaComExemplarGuardadoLiberaOExemplar() {
        BookCopy copy = circulatingCopy();
        Reservation r = heldFor(1L, now.plus(Duration.ofDays(1)), copy);
        when(reservations.findCopyIdById(7L)).thenReturn(Optional.of(10L));
        when(copies.findForUpdateById(10L)).thenReturn(Optional.of(copy));
        when(reservations.findForUpdateById(7L)).thenReturn(Optional.of(r));

        service.cancel(7L, 1L, false, "ip");

        assertEquals(ReservationStatus.CANCELADA, r.getStatus());
        assertEquals(CopyStatus.DISPONIVEL, copy.getStatus());
    }

    @Test
    void reservaNaoRetiradaNoPrazoExpiraELiberaOExemplar() {
        BookCopy copy = circulatingCopy();
        Reservation r = heldFor(1L, now.minus(Duration.ofMinutes(1)), copy);
        when(reservations.findExpiredIds(any())).thenReturn(List.of(7L));
        when(reservations.findCopyIdById(7L)).thenReturn(Optional.of(10L));
        when(copies.findForUpdateById(10L)).thenReturn(Optional.of(copy));
        when(reservations.findForUpdateById(7L)).thenReturn(Optional.of(r));

        service.expireOverdue();

        assertEquals(ReservationStatus.EXPIRADA, r.getStatus());
        assertEquals(CopyStatus.DISPONIVEL, copy.getStatus());
        verify(notifications).record(eq(1L), eq("RESERVATION_EXPIRED"), any());
    }
}

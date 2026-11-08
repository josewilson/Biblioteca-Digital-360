package br.com.biblioteca.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.biblioteca.acervo.Book;
import br.com.biblioteca.acervo.BookCopy;
import br.com.biblioteca.acervo.BookCopyRepository;
import br.com.biblioteca.acervo.CopyKind;
import br.com.biblioteca.acervo.CopyStatus;
import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.notification.NotificationService;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.user.Role;
import br.com.biblioteca.user.User;
import br.com.biblioteca.user.UserRepository;

class LoanServiceTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    /** Segunda-feira, 12:00 em São Paulo. */
    private final Clock clock = Clock.fixed(Instant.parse("2026-03-02T15:00:00Z"), ZONE);
    private final Instant now = clock.instant();

    private final LoanRepository loans = mock(LoanRepository.class);
    private final LoanRenewalRepository renewals = mock(LoanRenewalRepository.class);
    private final FineRepository fines = mock(FineRepository.class);
    private final LoanPolicyRepository policies = mock(LoanPolicyRepository.class);
    private final FineRateRepository fineRates = mock(FineRateRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BookCopyRepository copies = mock(BookCopyRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ReservationService reservations = mock(ReservationService.class);
    private final LoanService service = new LoanService(loans, renewals, fines,
            new BorrowerPolicies(policies, fineRates), users, copies, reservations, notifications,
            mock(AuditService.class), clock);

    private User student;

    @BeforeEach
    void setUp() {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn("STUDENT");
        student = mock(User.class);
        when(student.getId()).thenReturn(1L);
        when(student.isActive()).thenReturn(true);
        when(student.getRoles()).thenReturn(Set.of(role));
        when(users.findForUpdateById(1L)).thenReturn(Optional.of(student));
        when(users.findById(1L)).thenReturn(Optional.of(student));
        when(policies.findByRoleCodes(any())).thenReturn(List.of(new LoanPolicy(3, 14, 2, 7)));
        when(loans.save(any(Loan.class))).thenAnswer(i -> i.getArgument(0));
        when(fines.save(any(Fine.class))).thenAnswer(i -> i.getArgument(0));
    }

    private BookCopy copy(CopyKind kind) {
        BookCopy c = spy(new BookCopy("A-" + kind, kind, null, null));
        doReturn(new Book("Dom Casmurro")).when(c).getBook();
        doReturn(10L).when(c).getId();
        when(copies.findForUpdateById(10L)).thenReturn(Optional.of(c));
        return c;
    }

    private Loan activeLoan(BookCopy c, Instant due) {
        c.lend();
        Loan loan = new Loan(1L, c, 9L, now.minus(Duration.ofDays(30)), due);
        when(loans.findForUpdateById(5L)).thenReturn(Optional.of(loan));
        return loan;
    }

    // ---------- empréstimo ----------

    @Test
    void emprestaExemplarCirculanteComPrazoDoPerfil() {
        BookCopy c = copy(CopyKind.CIRCULANTE);

        LoanResponse r = service.lend(new LendRequest(1L, 10L), 9L, "ip");

        assertEquals(CopyStatus.EMPRESTADO, c.getStatus());
        assertEquals(now.plus(Duration.ofDays(14)), r.dueAt());
    }

    @Test
    void recusaQuandoOLimiteDeEmprestimosFoiAtingido() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        when(loans.countByUserIdAndReturnedAtIsNull(1L)).thenReturn(3L);

        assertThrows(BusinessRuleException.class, () -> service.lend(new LendRequest(1L, 10L), 9L, "ip"));
        assertEquals(CopyStatus.DISPONIVEL, c.getStatus());
    }

    @Test
    void recusaPerfilSemPoliticaDeEmprestimoComoVisitante() {
        copy(CopyKind.CIRCULANTE);
        when(policies.findByRoleCodes(any())).thenReturn(List.of());

        assertThrows(BusinessRuleException.class, () -> service.lend(new LendRequest(1L, 10L), 9L, "ip"));
    }

    @Test
    void recusaExemplarDeConsulta() {
        copy(CopyKind.CONSULTA);
        assertThrows(BusinessRuleException.class, () -> service.lend(new LendRequest(1L, 10L), 9L, "ip"));
        verify(loans, never()).save(any());
    }

    @Test
    void recusaExemplarJaEmprestado() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        c.lend();
        assertThrows(BusinessRuleException.class, () -> service.lend(new LendRequest(1L, 10L), 9L, "ip"));
    }

    @Test
    void recusaLeitorComEmprestimoAtrasado() {
        copy(CopyKind.CIRCULANTE);
        when(loans.existsByUserIdAndReturnedAtIsNullAndDueAtBefore(eq(1L), any())).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> service.lend(new LendRequest(1L, 10L), 9L, "ip"));
    }

    @Test
    void recusaLeitorComMultaEmAberto() {
        copy(CopyKind.CIRCULANTE);
        when(fines.existsOpenByUserId(1L)).thenReturn(true);
        assertThrows(BusinessRuleException.class, () -> service.lend(new LendRequest(1L, 10L), 9L, "ip"));
    }

    @Test
    void recusaLeitorInativo() {
        copy(CopyKind.CIRCULANTE);
        when(student.isActive()).thenReturn(false);
        assertThrows(BusinessRuleException.class, () -> service.lend(new LendRequest(1L, 10L), 9L, "ip"));
    }

    // ---------- devolução e multa ----------

    @Test
    void devolverNoPrazoNaoGeraMultaELiberaOExemplar() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        activeLoan(c, now.plus(Duration.ofDays(1)));

        ReturnResponse r = service.giveBack(5L, 9L, "ip");

        assertNull(r.fine());
        assertEquals(CopyStatus.DISPONIVEL, c.getStatus());
        assertNotNull(r.loan().returnedAt());
    }

    @Test
    void devolverComTresDiasDeAtrasoCobraTresDiariasENotificaOLeitor() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        activeLoan(c, now.minus(Duration.ofDays(3)));
        when(fineRates.findByRoleCodes(any())).thenReturn(List.of(new FineRate(new BigDecimal("1.00"))));

        ReturnResponse r = service.giveBack(5L, 9L, "ip");

        assertEquals(new BigDecimal("3.00"), r.fine().amount());
        assertEquals(3, r.fine().daysLate());
        verify(notifications).record(eq(1L), eq("FINE_REGISTERED"), any());
    }

    @Test
    void horasDeAtrasoNoMesmoDiaDoVencimentoNaoCobram() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        activeLoan(c, now.minus(Duration.ofHours(2)));   // vencia hoje às 10h, devolvido às 12h
        when(fineRates.findByRoleCodes(any())).thenReturn(List.of(new FineRate(new BigDecimal("1.00"))));

        assertNull(service.giveBack(5L, 9L, "ip").fine());
    }

    @Test
    void perfilSemValorDeMultaConfiguradoNaoPagaMulta() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        activeLoan(c, now.minus(Duration.ofDays(3)));
        when(fineRates.findByRoleCodes(any())).thenReturn(List.of());

        assertNull(service.giveBack(5L, 9L, "ip").fine());
        verify(fines, never()).save(any());
    }

    @Test
    void naoDevolveDuasVezes() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        Loan loan = activeLoan(c, now.plus(Duration.ofDays(1)));
        loan.markReturned(now);

        assertThrows(BusinessRuleException.class, () -> service.giveBack(5L, 9L, "ip"));
    }

    // ---------- renovação ----------

    @Test
    void renovaEstendendoOPrazoPeloPerfil() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        Instant due = now.plus(Duration.ofDays(2));
        activeLoan(c, due);

        LoanResponse r = service.renew(5L, 9L, "ip");

        assertEquals(due.plus(Duration.ofDays(7)), r.dueAt());
        verify(renewals).save(any(LoanRenewal.class));
    }

    @Test
    void naoRenovaEmprestimoAtrasado() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        activeLoan(c, now.minus(Duration.ofDays(1)));
        assertThrows(BusinessRuleException.class, () -> service.renew(5L, 9L, "ip"));
    }

    @Test
    void naoRenovaQuandoHaFilaDeReservasParaAObra() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        activeLoan(c, now.plus(Duration.ofDays(2)));
        when(reservations.hasQueue(any())).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> service.renew(5L, 9L, "ip"));
        verify(renewals, never()).save(any());
    }

    @Test
    void devolverAtendeAFilaDeReservasDaObra() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        activeLoan(c, now.plus(Duration.ofDays(1)));

        service.giveBack(5L, 9L, "ip");

        verify(reservations).serveQueue(any());
    }

    @Test
    void emprestarExemplarGuardadoPassaPelaConferenciaDaReserva() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        c.hold();
        org.mockito.Mockito.doAnswer(i -> {
            c.release();   // a retirada libera o exemplar guardado
            return null;
        }).when(reservations).claimHeldCopy(any(), eq(1L));

        service.lend(new LendRequest(1L, 10L), 9L, "ip");

        verify(reservations).claimHeldCopy(any(), eq(1L));
        assertEquals(CopyStatus.EMPRESTADO, c.getStatus());
    }

    @Test
    void naoRenovaAlemDoLimiteDeRenovacoes() {
        BookCopy c = copy(CopyKind.CIRCULANTE);
        activeLoan(c, now.plus(Duration.ofDays(2)));
        when(renewals.countByLoanId(anyLong())).thenReturn(2L);

        assertThrows(BusinessRuleException.class, () -> service.renew(5L, 9L, "ip"));
    }
}

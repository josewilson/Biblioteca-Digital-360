package br.com.biblioteca.loan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.notification.NotificationService;
import br.com.biblioteca.shared.error.BusinessRuleException;

class FineServiceTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-03-02T15:00:00Z"), ZoneId.of("America/Sao_Paulo"));
    private final FineRepository fines = mock(FineRepository.class);
    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final FineService service = new FineService(fines, payments, notifications, mock(AuditService.class), clock);

    private Fine fine;

    @BeforeEach
    void setUp() {
        Loan loan = new Loan(1L, null, 9L, clock.instant(), clock.instant().plusSeconds(60));
        fine = new Fine(loan, new BigDecimal("3.00"), 3);
        when(fines.findForUpdateById(5L)).thenReturn(Optional.of(fine));
        when(payments.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void pagaOValorIntegralMarcaComoPagaENotifica() {
        PaymentResponse r = service.pay(5L, PaymentMethod.PIX, 9L, "ip");

        assertEquals(new BigDecimal("3.00"), r.amount());
        assertEquals(PaymentMethod.PIX, r.method());
        assertEquals(FineStatus.PAGA, fine.getStatus());
        verify(notifications).record(eq(1L), eq("PAYMENT_CONFIRMED"), any());
    }

    @Test
    void naoPagaDuasVezesAMesmaMulta() {
        service.pay(5L, PaymentMethod.DINHEIRO, 9L, "ip");

        assertThrows(BusinessRuleException.class, () -> service.pay(5L, PaymentMethod.DINHEIRO, 9L, "ip"));
    }

    @Test
    void isentaComJustificativaEResponsavel() {
        FineResponse r = service.waive(5L, "  Doença comprovada  ", 9L, "ip");

        assertEquals(FineStatus.ISENTA, r.status());
        assertEquals("Doença comprovada", r.waiverReason());
    }

    @Test
    void multaIsentaNaoPodeSerPaga() {
        service.waive(5L, "motivo", 9L, "ip");
        assertThrows(BusinessRuleException.class, () -> service.pay(5L, PaymentMethod.PIX, 9L, "ip"));
        verify(payments, never()).save(any());
    }

    @Test
    void multaPagaNaoPodeSerIsentada() {
        service.pay(5L, PaymentMethod.PIX, 9L, "ip");
        assertThrows(BusinessRuleException.class, () -> service.waive(5L, "motivo", 9L, "ip"));
    }
}

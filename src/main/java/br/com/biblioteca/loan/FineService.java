package br.com.biblioteca.loan;

import java.time.Clock;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.notification.NotificationService;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

/** Pagamento e isenção de multas. Cada multa só sai de ABERTA uma vez (a trava impede pagamento duplo). */
@Service
public class FineService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final FineRepository fines;
    private final PaymentRepository payments;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    FineService(FineRepository fines, PaymentRepository payments, NotificationService notifications,
                AuditService audit, Clock clock) {
        this.fines = fines;
        this.payments = payments;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    /** Paga o valor integral da multa. */
    @Transactional
    public PaymentResponse pay(Long fineId, PaymentMethod method, Long callerId, String ip) {
        Fine fine = lockedFine(fineId);
        fine.markPaid();   // recusa se já paga/isenta
        Payment payment = payments.save(new Payment(fine, fine.getAmount(), method, callerId, clock.instant()));
        notifications.record(fine.getLoan().getUserId(), "PAYMENT_CONFIRMED",
                String.format(PT_BR, "Pagamento de R$ %.2f confirmado.", fine.getAmount()));
        audit.record(callerId, "FINE_PAID", "fine", fineId, "forma=" + method, ip);
        return PaymentResponse.from(payment);
    }

    @Transactional
    public FineResponse waive(Long fineId, String reason, Long callerId, String ip) {
        Fine fine = lockedFine(fineId);
        fine.waive(reason.trim(), callerId);
        audit.record(callerId, "FINE_WAIVED", "fine", fineId, reason.trim(), ip);
        return FineResponse.from(fine);
    }

    @Transactional(readOnly = true)
    public Page<FineResponse> list(Long userId, FineStatus status, Pageable pageable) {
        Page<Fine> page;
        if (userId != null) {
            page = status == null ? fines.findByLoanUserId(userId, pageable)
                    : fines.findByLoanUserIdAndStatus(userId, status, pageable);
        } else {
            page = status == null ? fines.findAll(pageable) : fines.findByStatus(status, pageable);
        }
        return page.map(FineResponse::from);
    }

    @Transactional(readOnly = true)
    public PaymentResponse receipt(Long paymentId) {
        return PaymentResponse.from(payments.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Pagamento " + paymentId + " não existe.")));
    }

    private Fine lockedFine(Long id) {
        return fines.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Multa " + id + " não existe."));
    }
}

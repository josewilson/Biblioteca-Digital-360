package br.com.biblioteca.loan;

import java.math.BigDecimal;
import java.time.Instant;

/** Dados do comprovante de pagamento. */
public record PaymentResponse(Long id, Long fineId, Long loanId, Long userId, BigDecimal amount,
                              PaymentMethod method, Long receivedBy, Instant paidAt) {

    static PaymentResponse from(Payment p) {
        Fine f = p.getFine();
        return new PaymentResponse(p.getId(), f.getId(), f.getLoan().getId(), f.getLoan().getUserId(),
                p.getAmount(), p.getMethod(), p.getReceivedBy(), p.getPaidAt());
    }
}

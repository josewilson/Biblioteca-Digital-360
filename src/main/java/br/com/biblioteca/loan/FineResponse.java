package br.com.biblioteca.loan;

import java.math.BigDecimal;

public record FineResponse(Long id, Long loanId, Long userId, BigDecimal amount, int daysLate, FineStatus status,
                           String waiverReason) {

    static FineResponse from(Fine f) {
        return new FineResponse(f.getId(), f.getLoan().getId(), f.getLoan().getUserId(), f.getAmount(),
                f.getDaysLate(), f.getStatus(), f.getWaiverReason());
    }
}

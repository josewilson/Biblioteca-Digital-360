package br.com.biblioteca.loan;

import java.time.Instant;

public record LoanResponse(Long id, Long userId, Long copyId, String barcode, String bookTitle,
                           Instant loanedAt, Instant dueAt, Instant returnedAt) {

    static LoanResponse from(Loan l) {
        return new LoanResponse(l.getId(), l.getUserId(), l.getCopy().getId(), l.getCopy().getBarcode(),
                l.getCopy().getBook().getTitle(), l.getLoanedAt(), l.getDueAt(), l.getReturnedAt());
    }
}

package br.com.biblioteca.loan;

import java.math.BigDecimal;

public record PolicyResponse(String role, int maxLoans, int loanDays, int maxRenewals, int renewalDays,
                             int reservationPickupDays, BigDecimal dailyRate) {

    static PolicyResponse from(LoanPolicy p, BigDecimal dailyRate) {
        return new PolicyResponse(p.getRoleCode(), p.getMaxLoans(), p.getLoanDays(), p.getMaxRenewals(),
                p.getRenewalDays(), p.getReservationPickupDays(), dailyRate);
    }
}

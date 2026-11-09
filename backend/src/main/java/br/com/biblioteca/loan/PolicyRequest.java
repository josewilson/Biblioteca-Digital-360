package br.com.biblioteca.loan;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record PolicyRequest(
        @Min(1) @Max(100) int maxLoans,
        @Min(1) @Max(365) int loanDays,
        @Min(0) @Max(20) int maxRenewals,
        @Min(1) @Max(365) int renewalDays,
        @Min(1) @Max(60) int reservationPickupDays,
        @NotNull @DecimalMin("0.00") @DecimalMax("999.99") @Digits(integer = 3, fraction = 2) BigDecimal dailyRate) {
}

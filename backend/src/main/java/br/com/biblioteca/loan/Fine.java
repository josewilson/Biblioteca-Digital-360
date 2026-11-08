package br.com.biblioteca.loan;

import java.math.BigDecimal;
import java.time.Instant;

import br.com.biblioteca.shared.error.BusinessRuleException;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Multa de um empréstimo (no máximo uma por empréstimo: UNIQUE no banco). */
@Entity
@Table(name = "fines")
public class Fine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    private BigDecimal amount;
    private int daysLate;

    @Enumerated(EnumType.STRING)
    private FineStatus status = FineStatus.ABERTA;

    private String waiverReason;
    private Long waivedBy;
    private Instant createdAt = Instant.now();

    protected Fine() {
    }

    Fine(Loan loan, BigDecimal amount, int daysLate) {
        this.loan = loan;
        this.amount = amount;
        this.daysLate = daysLate;
    }

    void markPaid() {
        requireOpen();
        status = FineStatus.PAGA;
    }

    void waive(String reason, Long authorizedBy) {
        requireOpen();
        status = FineStatus.ISENTA;
        waiverReason = reason;
        waivedBy = authorizedBy;
    }

    private void requireOpen() {
        if (status != FineStatus.ABERTA) {
            throw new BusinessRuleException("Esta multa não está em aberto (situação: " + status + ").");
        }
    }

    public Long getId() {
        return id;
    }

    public Loan getLoan() {
        return loan;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getDaysLate() {
        return daysLate;
    }

    public FineStatus getStatus() {
        return status;
    }

    public String getWaiverReason() {
        return waiverReason;
    }
}

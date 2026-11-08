package br.com.biblioteca.loan;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "loan_renewals")
class LoanRenewal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id")
    private Loan loan;

    private Instant oldDueAt;
    private Instant newDueAt;
    private Long renewedBy;
    private Instant createdAt = Instant.now();

    protected LoanRenewal() {
    }

    LoanRenewal(Loan loan, Instant oldDueAt, Instant newDueAt, Long renewedBy) {
        this.loan = loan;
        this.oldDueAt = oldDueAt;
        this.newDueAt = newDueAt;
        this.renewedBy = renewedBy;
    }
}

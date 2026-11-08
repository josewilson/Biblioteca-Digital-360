package br.com.biblioteca.loan;

import java.math.BigDecimal;
import java.time.Instant;

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

/** Pagamento de uma multa (um por multa: UNIQUE no banco). */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fine_id")
    private Fine fine;

    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentMethod method;

    private Long receivedBy;
    private Instant paidAt;

    protected Payment() {
    }

    Payment(Fine fine, BigDecimal amount, PaymentMethod method, Long receivedBy, Instant paidAt) {
        this.fine = fine;
        this.amount = amount;
        this.method = method;
        this.receivedBy = receivedBy;
        this.paidAt = paidAt;
    }

    public Long getId() {
        return id;
    }

    public Fine getFine() {
        return fine;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public Long getReceivedBy() {
        return receivedBy;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}

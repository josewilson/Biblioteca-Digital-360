package br.com.biblioteca.loan;

import java.time.Instant;

import br.com.biblioteca.acervo.BookCopy;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "loans")
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "copy_id")
    private BookCopy copy;

    private Long performedBy;
    private Instant loanedAt;
    private Instant dueAt;
    private Instant returnedAt;
    private Instant createdAt = Instant.now();

    protected Loan() {
    }

    Loan(Long userId, BookCopy copy, Long performedBy, Instant loanedAt, Instant dueAt) {
        this.userId = userId;
        this.copy = copy;
        this.performedBy = performedBy;
        this.loanedAt = loanedAt;
        this.dueAt = dueAt;
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    void markReturned(Instant when) {
        this.returnedAt = when;
    }

    void extendTo(Instant newDueAt) {
        this.dueAt = newDueAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BookCopy getCopy() {
        return copy;
    }

    public Instant getLoanedAt() {
        return loanedAt;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }
}

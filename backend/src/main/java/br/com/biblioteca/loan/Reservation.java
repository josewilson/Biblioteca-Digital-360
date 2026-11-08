package br.com.biblioteca.loan;

import java.time.Instant;

import br.com.biblioteca.acervo.Book;
import br.com.biblioteca.acervo.BookCopy;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Reserva de uma obra. A fila segue a ordem de cadastro (id crescente). */
@Entity
@Table(name = "reservations")
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private ReservationStatus status = ReservationStatus.NA_FILA;

    private Instant queuedAt;
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copy_id")
    private BookCopy copy;                 // exemplar guardado enquanto DISPONIVEL

    private Instant createdAt = Instant.now();

    protected Reservation() {
    }

    Reservation(Book book, Long userId, Instant queuedAt) {
        this.book = book;
        this.userId = userId;
        this.queuedAt = queuedAt;
    }

    /** A reserva chegou ao topo da fila e há exemplar guardado para retirada até {@code until}. */
    void holdCopy(BookCopy copy, Instant until) {
        this.copy = copy;
        this.expiresAt = until;
        this.status = ReservationStatus.DISPONIVEL;
    }

    void markPickedUp() {
        status = ReservationStatus.RETIRADA;
    }

    void markCancelled() {
        status = ReservationStatus.CANCELADA;
    }

    void markExpired() {
        status = ReservationStatus.EXPIRADA;
    }

    public Long getId() {
        return id;
    }

    public Book getBook() {
        return book;
    }

    public Long getUserId() {
        return userId;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public BookCopy getCopy() {
        return copy;
    }
}

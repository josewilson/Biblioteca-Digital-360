package br.com.biblioteca.acervo;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** Exemplar físico de uma obra. */
@Entity
@Table(name = "book_copies")
public class BookCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id")
    private Book book;

    private String barcode;

    @Enumerated(EnumType.STRING)
    private CopyKind kind;

    @Enumerated(EnumType.STRING)
    private CopyStatus status = CopyStatus.DISPONIVEL;

    @Enumerated(EnumType.STRING)
    private CopyCondition condition;

    private String location;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected BookCopy() {
    }

    public BookCopy(String barcode, CopyKind kind, CopyCondition condition, String location) {
        this.barcode = barcode;
        this.kind = kind;
        this.condition = condition == null ? CopyCondition.BOM : condition;
        this.location = location;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    void setBook(Book book) {
        this.book = book;
    }

    void setStatus(CopyStatus status) {
        this.status = status;
    }

    /** Só exemplar circulante e disponível sai da biblioteca. */
    public void lend() {
        if (kind != CopyKind.CIRCULANTE) {
            throw new BusinessRuleException("Exemplar de consulta nunca sai da biblioteca.");
        }
        if (status != CopyStatus.DISPONIVEL) {
            throw new BusinessRuleException("Exemplar não está disponível (situação: " + status + ").");
        }
        status = CopyStatus.EMPRESTADO;
    }

    /** Guarda o exemplar para o 1º da fila de reservas. */
    public void hold() {
        if (kind != CopyKind.CIRCULANTE || status != CopyStatus.DISPONIVEL) {
            throw new BusinessRuleException("Só exemplar circulante disponível pode ser guardado para reserva.");
        }
        status = CopyStatus.RESERVADO;
    }

    /** Desfaz a guarda (retirada, cancelamento ou expiração da reserva). */
    public void release() {
        if (status != CopyStatus.RESERVADO) {
            throw new BusinessRuleException("Exemplar não está reservado.");
        }
        status = CopyStatus.DISPONIVEL;
    }

    public void giveBack() {
        if (status != CopyStatus.EMPRESTADO) {
            throw new BusinessRuleException("Exemplar não está emprestado.");
        }
        status = CopyStatus.DISPONIVEL;
    }

    public Book getBook() {
        return book;
    }

    public Long getId() {
        return id;
    }

    public String getBarcode() {
        return barcode;
    }

    public CopyKind getKind() {
        return kind;
    }

    public CopyStatus getStatus() {
        return status;
    }

    public CopyCondition getCondition() {
        return condition;
    }

    public String getLocation() {
        return location;
    }
}

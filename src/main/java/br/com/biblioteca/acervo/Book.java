package br.com.biblioteca.acervo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** A obra bibliográfica (não o exemplar físico). */
@Entity
@Table(name = "books")
public class Book {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String isbn;
    private String edition;
    private Short publicationYear;
    private String language;
    private String description;
    private String coverUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "publisher_id")
    private Publisher publisher;

    @ManyToMany
    @JoinTable(name = "book_authors", joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "author_id"))
    @BatchSize(size = 50)
    private Set<Author> authors = new HashSet<>();

    @ManyToMany
    @JoinTable(name = "book_categories", joinColumns = @JoinColumn(name = "book_id"),
            inverseJoinColumns = @JoinColumn(name = "category_id"))
    @BatchSize(size = 50)
    private Set<Category> categories = new HashSet<>();

    @OneToMany(mappedBy = "book", cascade = CascadeType.ALL)
    @BatchSize(size = 50)
    private List<BookCopy> copies = new ArrayList<>();

    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected Book() {
    }

    public Book(String title) {
        this.title = title;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    void describe(String isbn, String edition, Integer year, String language, String description, String coverUrl) {
        this.isbn = isbn;
        this.edition = edition;
        this.publicationYear = year == null ? null : year.shortValue();
        this.language = language;
        this.description = description;
        this.coverUrl = coverUrl;
    }

    void setPublisher(Publisher publisher) {
        this.publisher = publisher;
    }

    void setAuthors(Set<Author> authors) {
        this.authors = authors;
    }

    void setCategories(Set<Category> categories) {
        this.categories = categories;
    }

    void addCopy(BookCopy copy) {
        copies.add(copy);
        copy.setBook(this);
    }

    /** Invariante: a obra sempre mantém ao menos um exemplar de consulta ativo. */
    boolean hasOtherActiveConsultaCopy(BookCopy copy) {
        return copies.stream()
                .anyMatch(c -> c != copy && c.getKind() == CopyKind.CONSULTA && c.getStatus().isActive());
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getEdition() {
        return edition;
    }

    public Short getPublicationYear() {
        return publicationYear;
    }

    public String getLanguage() {
        return language;
    }

    public String getDescription() {
        return description;
    }

    public String getCoverUrl() {
        return coverUrl;
    }

    public Publisher getPublisher() {
        return publisher;
    }

    public Set<Author> getAuthors() {
        return authors;
    }

    public Set<Category> getCategories() {
        return categories;
    }

    public List<BookCopy> getCopies() {
        return copies;
    }
}

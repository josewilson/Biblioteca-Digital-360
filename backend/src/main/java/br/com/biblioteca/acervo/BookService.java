package br.com.biblioteca.acervo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.SearchText;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

@Service
public class BookService {

    /** Estados que a equipe pode definir à mão. EMPRESTADO/RESERVADO pertencem ao fluxo de empréstimo. */
    private static final Set<CopyStatus> MANUAL_STATUSES =
            Set.of(CopyStatus.DISPONIVEL, CopyStatus.PERDIDO, CopyStatus.BAIXADO);

    private final BookRepository books;
    private final BookCopyRepository copies;
    private final AuthorRepository authors;
    private final PublisherRepository publishers;
    private final CategoryRepository categories;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    BookService(BookRepository books, BookCopyRepository copies, AuthorRepository authors,
                PublisherRepository publishers, CategoryRepository categories, AuditService audit,
                ApplicationEventPublisher events) {
        this.events = events;
        this.books = books;
        this.copies = copies;
        this.authors = authors;
        this.publishers = publishers;
        this.categories = categories;
        this.audit = audit;
    }

    @Transactional
    public BookResponse create(CreateBookRequest req, Long callerId, String ip) {
        requireConsultaAndCirculante(req.copies());
        requireNewBarcodes(req.copies().stream().map(CopyRequest::barcode).toList());
        String isbn = blankToNull(req.isbn());
        if (isbn != null && books.existsByIsbn(isbn)) {
            throw new BusinessRuleException("Já existe obra com este ISBN.");
        }

        Book book = new Book(req.title().trim());
        applyDetails(book, req, isbn);
        req.copies().forEach(c -> book.addCopy(toCopy(c)));

        books.save(book);
        audit.record(callerId, "BOOK_CREATED", "book", book.getId(), "exemplares=" + req.copies().size(), ip);
        return BookResponse.from(book);
    }

    @Transactional
    public BookResponse update(Long id, UpdateBookRequest req, Long callerId, String ip) {
        Book book = findBook(id);
        String isbn = blankToNull(req.isbn());
        if (isbn != null && books.existsByIsbnAndIdNot(isbn, id)) {
            throw new BusinessRuleException("Já existe obra com este ISBN.");
        }
        book.setTitle(req.title().trim());
        applyDetails(book, req, isbn);
        audit.record(callerId, "BOOK_UPDATED", "book", id, null, ip);
        return BookResponse.from(book);
    }

    @Transactional
    public CopyResponse updateCopy(Long copyId, UpdateCopyRequest req, Long callerId, String ip) {
        BookCopy copy = findCopy(copyId);
        String barcode = req.barcode().trim();
        if (copies.existsByBarcodeAndIdNot(barcode, copyId)) {
            throw new BusinessRuleException("Já existe exemplar com este código.");
        }
        copy.update(barcode, req.condition(), blankToNull(req.location()));
        audit.record(callerId, "COPY_UPDATED", "book_copy", copyId, null, ip);
        return CopyResponse.from(copy);
    }

    @Transactional
    public CopyResponse addCopy(Long bookId, CopyRequest req, Long callerId, String ip) {
        Book book = findBook(bookId);
        requireNewBarcodes(List.of(req.barcode()));
        BookCopy copy = toCopy(req);
        book.addCopy(copy);
        copies.save(copy);
        audit.record(callerId, "COPY_ADDED", "book_copy", copy.getId(), "obra=" + bookId, ip);
        if (copy.getKind() == CopyKind.CIRCULANTE) {
            events.publishEvent(new CopyBecameAvailable(bookId));   // atende a fila de reservas, se houver
        }
        return CopyResponse.from(copy);
    }

    @Transactional
    public CopyResponse changeStatus(Long copyId, CopyStatus status, Long callerId, String ip) {
        BookCopy copy = findCopy(copyId);
        if (!MANUAL_STATUSES.contains(status)) {
            throw new BusinessRuleException("Este estado só muda por empréstimo ou reserva.");
        }
        if (copy.getStatus() == CopyStatus.EMPRESTADO || copy.getStatus() == CopyStatus.RESERVADO) {
            throw new BusinessRuleException("Exemplar emprestado ou reservado: conclua a operação primeiro.");
        }
        boolean leavesCirculation = !status.isActive();
        if (leavesCirculation && copy.getKind() == CopyKind.CONSULTA && copy.getStatus().isActive()
                && !copy.getBook().hasOtherActiveConsultaCopy(copy)) {
            throw new BusinessRuleException(
                    "Esta é o único exemplar de consulta da obra. Cadastre outro exemplar de consulta antes.");
        }
        CopyStatus before = copy.getStatus();
        copy.setStatus(status);
        audit.record(callerId, "COPY_STATUS_CHANGED", "book_copy", copyId, "para=" + status, ip);
        if (status == CopyStatus.DISPONIVEL && before != CopyStatus.DISPONIVEL && copy.getKind() == CopyKind.CIRCULANTE) {
            events.publishEvent(new CopyBecameAvailable(copy.getBook().getId()));
        }
        return CopyResponse.from(copy);
    }

    @Transactional(readOnly = true)
    public Page<BookResponse> search(String query, Pageable pageable) {
        Page<Book> page = (query == null || query.isBlank())
                ? books.findAll(pageable)
                : books.search(SearchText.contains(query), query.trim(), pageable);
        return page.map(BookResponse::from);
    }

    @Transactional(readOnly = true)
    public BookResponse get(Long id) {
        return BookResponse.from(findBook(id));
    }

    private void applyDetails(Book book, BookDetails d, String isbn) {
        book.describe(isbn, d.edition(), d.publicationYear(), d.language(), d.description(), d.coverUrl());
        book.setPublisher(d.publisherId() == null ? null : publishers.findById(d.publisherId())
                .orElseThrow(() -> new ResourceNotFoundException("Editora inexistente.")));
        book.setAuthors(new HashSet<>(findAll(authors, d.authorIds(), "Autor")));
        book.setCategories(new HashSet<>(findAll(categories, d.categoryIds(), "Categoria")));
    }

    private BookCopy findCopy(Long id) {
        return copies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exemplar " + id + " não existe."));
    }

    private Book findBook(Long id) {
        return books.findById(id).orElseThrow(() -> new ResourceNotFoundException("Obra " + id + " não existe."));
    }

    /** Regra central: toda obra tem pelo menos 1 exemplar de consulta e pelo menos 1 emprestável. */
    private void requireConsultaAndCirculante(List<CopyRequest> list) {
        boolean consulta = list.stream().anyMatch(c -> c.kind() == CopyKind.CONSULTA);
        boolean circulante = list.stream().anyMatch(c -> c.kind() == CopyKind.CIRCULANTE);
        if (!consulta || !circulante) {
            throw new BusinessRuleException(
                    "Toda obra precisa de pelo menos 1 exemplar de consulta e 1 exemplar para empréstimo.");
        }
    }

    private void requireNewBarcodes(List<String> barcodes) {
        if (new HashSet<>(barcodes).size() != barcodes.size()) {
            throw new BusinessRuleException("Há códigos de exemplar repetidos na requisição.");
        }
        if (copies.existsByBarcodeIn(barcodes)) {
            throw new BusinessRuleException("Já existe exemplar com um desses códigos.");
        }
    }

    private BookCopy toCopy(CopyRequest r) {
        return new BookCopy(r.barcode().trim(), r.kind(), r.condition(), r.location());
    }

    private static <T> List<T> findAll(JpaRepository<T, Long> repo, Set<Long> ids, String label) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<T> found = repo.findAllById(ids);
        if (found.size() != ids.size()) {
            throw new ResourceNotFoundException(label + " inexistente.");
        }
        return found;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}

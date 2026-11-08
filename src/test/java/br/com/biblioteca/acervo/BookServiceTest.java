package br.com.biblioteca.acervo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.error.BusinessRuleException;

class BookServiceTest {

    private final BookRepository books = mock(BookRepository.class);
    private final BookCopyRepository copies = mock(BookCopyRepository.class);
    private final BookService service = new BookService(books, copies, mock(AuthorRepository.class),
            mock(PublisherRepository.class), mock(CategoryRepository.class), mock(AuditService.class),
            mock(org.springframework.context.ApplicationEventPublisher.class));

    private CopyRequest copy(String barcode, CopyKind kind) {
        return new CopyRequest(barcode, kind, null, null);
    }

    private CreateBookRequest book(CopyRequest... copies) {
        return new CreateBookRequest("Dom Casmurro", null, null, null, null, null, null, null, null, null,
                List.of(copies));
    }

    @Test
    void criaObraComUmExemplarDeConsultaEUmEmprestavel() {
        BookResponse r = service.create(book(copy("A1", CopyKind.CONSULTA), copy("A2", CopyKind.CIRCULANTE)), 1L, "ip");

        assertEquals(2, r.copies().size());
        verify(books).save(any(Book.class));
    }

    @Test
    void recusaObraSemExemplarDeConsulta() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(book(copy("A1", CopyKind.CIRCULANTE), copy("A2", CopyKind.CIRCULANTE)), 1L, "ip"));
    }

    @Test
    void recusaObraSoComExemplarDeConsulta() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(book(copy("A1", CopyKind.CONSULTA)), 1L, "ip"));
    }

    @Test
    void recusaCodigoRepetidoNaMesmaRequisicao() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(book(copy("A1", CopyKind.CONSULTA), copy("A1", CopyKind.CIRCULANTE)), 1L, "ip"));
    }

    @Test
    void recusaCodigoQueJaExisteNoAcervo() {
        when(copies.existsByBarcodeIn(any())).thenReturn(true);
        assertThrows(BusinessRuleException.class,
                () -> service.create(book(copy("A1", CopyKind.CONSULTA), copy("A2", CopyKind.CIRCULANTE)), 1L, "ip"));
    }

    private BookCopy copyOf(Book book, String barcode, CopyKind kind) {
        BookCopy c = new BookCopy(barcode, kind, null, null);
        book.addCopy(c);
        return c;
    }

    @Test
    void naoDaBaixaNoUnicoExemplarDeConsulta() {
        Book book = new Book("Dom Casmurro");
        BookCopy consulta = copyOf(book, "A1", CopyKind.CONSULTA);
        copyOf(book, "A2", CopyKind.CIRCULANTE);
        when(copies.findById(1L)).thenReturn(Optional.of(consulta));

        assertThrows(BusinessRuleException.class, () -> service.changeStatus(1L, CopyStatus.BAIXADO, 1L, "ip"));
        assertEquals(CopyStatus.DISPONIVEL, consulta.getStatus());
    }

    @Test
    void daBaixaNoExemplarDeConsultaSeHaOutroAtivo() {
        Book book = new Book("Dom Casmurro");
        BookCopy primeiro = copyOf(book, "A1", CopyKind.CONSULTA);
        copyOf(book, "A3", CopyKind.CONSULTA);
        when(copies.findById(1L)).thenReturn(Optional.of(primeiro));

        service.changeStatus(1L, CopyStatus.BAIXADO, 1L, "ip");

        assertEquals(CopyStatus.BAIXADO, primeiro.getStatus());
    }

    @Test
    void emprestadoEReservadoNaoSaoDefinidosAMao() {
        Book book = new Book("Dom Casmurro");
        BookCopy circulante = copyOf(book, "A2", CopyKind.CIRCULANTE);
        when(copies.findById(2L)).thenReturn(Optional.of(circulante));

        assertThrows(BusinessRuleException.class, () -> service.changeStatus(2L, CopyStatus.EMPRESTADO, 1L, "ip"));
    }
}

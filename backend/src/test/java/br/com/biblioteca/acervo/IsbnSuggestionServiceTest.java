package br.com.biblioteca.acervo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import br.com.biblioteca.acervo.IsbnSuggestion.Match;
import br.com.biblioteca.shared.error.ExternalServiceException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

/** Mapeamento, mescla, casamento de nomes e fallback, sem rede e sem banco. */
class IsbnSuggestionServiceTest {

    private static final String ISBN = "9788545702870";

    private final BookRepository books = mock(BookRepository.class);
    private final AuthorRepository authors = mock(AuthorRepository.class);
    private final PublisherRepository publishers = mock(PublisherRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);

    private record Fake(String name, AtomicInteger calls, Supplier<Optional<BookData>> answer) implements BookDataSource {
        Fake(String name, Supplier<Optional<BookData>> answer) {
            this(name, new AtomicInteger(), answer);
        }

        @Override
        public Optional<BookData> find(String isbn13) {
            calls.incrementAndGet();
            return answer.get();
        }
    }

    private static Author author(long id, String name) {
        Author a = mock(Author.class);
        when(a.getId()).thenReturn(id);
        when(a.getName()).thenReturn(name);
        return a;
    }

    private static Publisher publisher(long id, String name) {
        Publisher p = mock(Publisher.class);
        when(p.getId()).thenReturn(id);
        when(p.getName()).thenReturn(name);
        return p;
    }

    private static Category category(long id, String name) {
        Category c = mock(Category.class);
        when(c.getId()).thenReturn(id);
        when(c.getName()).thenReturn(name);
        return c;
    }

    private IsbnSuggestionService service(BookDataSource... sources) {
        return new IsbnSuggestionService(List.of(sources), books, authors, publishers, categories);
    }

    @BeforeEach
    void cadastroPadrao() {
        when(books.findIdsByIsbnIn(any())).thenReturn(List.of());
        List<Author> registeredAuthors = List.of(author(9, "Otomo, Katsuhiro"), author(10, "Outra Pessoa"));
        List<Publisher> registeredPublishers = List.of(publisher(5, "Japorama"));
        List<Category> registeredCategories = List.of(category(3, "Mangá"));
        when(authors.findAll(any(Sort.class))).thenReturn(registeredAuthors);
        when(publishers.findAll(any(Sort.class))).thenReturn(registeredPublishers);
        when(categories.findAll(any(Sort.class))).thenReturn(registeredCategories);
    }

    private static BookData brasil() {
        return new BookData("Akira", null, List.of("KATSUHIRO OTOMO", "DRIK SADA", "Katsuhiro Otomo"),
                "Japorama Editora e Comunicação", 2017, null, null, "<p>Um marco &amp; clássico</p>", null,
                List.of("mangá", "motocicleta", "gangue", "delinquência", "futuro", "cidade", "Mangá"));
    }

    private static BookData openLibrary() {
        return new BookData("Akira vol. 1", "Part 1", List.of(), "Editora JBC", 2016, "Português", null,
                "Descrição da Open Library", "http://covers.openlibrary.org/b/id/1-L.jpg", List.of());
    }

    @Test
    void usaOPrimeiroProvedorEComplementaComOSegundoRegistrandoDeOndeVeio() {
        IsbnSuggestion s = service(new Fake("brasilapi", () -> Optional.of(brasil())),
                new Fake("openlibrary", () -> Optional.of(openLibrary()))).lookup("978-85-4570-287-0");

        assertFalse(s.alreadyRegistered());
        assertEquals(ISBN, s.isbn());
        assertEquals("Akira", s.title());   // título e subtítulo do mesmo provedor, não misturados
        assertEquals(2017, s.publicationYear());
        assertEquals("Português", s.language());
        assertEquals("Um marco & clássico", s.description());   // sem HTML
        assertEquals("https://covers.openlibrary.org/b/id/1-L.jpg", s.coverUrl());   // https forçado
        assertNull(s.edition());   // ninguém trouxe: não inventa
        assertEquals("brasilapi", s.sources().get("title"));
        assertEquals("openlibrary", s.sources().get("language"));
        assertEquals("openlibrary", s.sources().get("coverUrl"));
        assertEquals("brasilapi", s.sources().get("description"));
    }

    @Test
    void casaEditoraAutoresECategoriasComOCadastro() {
        IsbnSuggestion s = service(new Fake("brasilapi", () -> Optional.of(brasil()))).lookup(ISBN);

        assertEquals(new Match(5L, "Japorama"), s.publisher());   // "Editora e Comunicação" não atrapalha
        assertEquals(List.of(new Match(9L, "Otomo, Katsuhiro"),   // MAIÚSCULAS e ordem invertida casam; repetido some
                new Match(null, "Drik Sada")), s.authors());
        assertEquals(new Match(3L, "Mangá"), s.categories().get(0));   // as cadastradas vêm primeiro
        assertEquals(1 + 5, s.categories().size());   // e só 5 nomes novos (o resto é ruído)
        assertTrue(s.categories().stream().skip(1).allMatch(m -> m.id() == null));
    }

    @Test
    void editoraNovaVemSoComoNomeSugerido() {
        when(publishers.findAll(any(Sort.class))).thenReturn(List.of());
        IsbnSuggestion s = service(new Fake("brasilapi", () -> Optional.of(brasil()))).lookup(ISBN);
        assertEquals(new Match(null, "Japorama Editora e Comunicação"), s.publisher());
    }

    @Test
    void provedorForaDoArNaoDerrubaABusca() {
        IsbnSuggestion s = service(new Fake("brasilapi", () -> { throw new ExternalServiceException("caiu"); }),
                new Fake("openlibrary", () -> Optional.of(openLibrary()))).lookup(ISBN);
        assertEquals("Akira vol. 1: Part 1", s.title());
        assertEquals("openlibrary", s.sources().get("title"));
    }

    @Test
    void naoConheceOuForaDoArDaoErrosDiferentes() {
        Fake semLivro = new Fake("a", Optional::empty);
        assertThrows(ResourceNotFoundException.class, () -> service(semLivro, new Fake("b", Optional::empty)).lookup(ISBN));
        assertThrows(ExternalServiceException.class, () -> service(semLivro,
                new Fake("b", () -> { throw new ExternalServiceException("caiu"); })).lookup(ISBN));
    }

    @Test
    void isbnJaCadastradoAvisaENaoConsultaNinguem() {
        when(books.findIdsByIsbnIn(any())).thenReturn(List.of(7L));
        Fake fonte = new Fake("brasilapi", () -> Optional.of(brasil()));

        IsbnSuggestion s = service(fonte).lookup(ISBN);

        assertTrue(s.alreadyRegistered());
        assertEquals(7L, s.existingBookId());
        assertEquals(0, fonte.calls().get());
    }

    @Test
    void descartaAnoForaDaFaixaEEnderecoDeCapaPerigoso() {
        BookData ruim = new BookData("Livro", null, List.of(), null, 99, null, null, null, "javascript:alert(1)", List.of());
        IsbnSuggestion s = service(new Fake("brasilapi", () -> Optional.of(ruim))).lookup(ISBN);
        assertNull(s.publicationYear());
        assertNull(s.coverUrl());
    }

    @Test
    void normalizacoes() {
        assertEquals("Katsuhiro Otomo", IsbnSuggestionService.prettify("KATSUHIRO OTOMO"));
        assertEquals("Maria da Silva", IsbnSuggestionService.prettify("MARIA DA SILVA"));
        assertEquals("George R. R. Martin", IsbnSuggestionService.prettify("George R. R. Martin"));   // já tem minúsculas
        assertEquals(IsbnSuggestionService.authorKey("George Orwell"), IsbnSuggestionService.authorKey("Orwell, George"));
        assertEquals("companhia das letras", IsbnSuggestionService.publisherKey("Companhia das Letras Ltda"));
        assertEquals("editora", IsbnSuggestionService.publisherKey("Editora"));   // só ruído: mantém
        assertEquals("a\nb", IsbnSuggestionService.plainText("<p>a</p><p>b</p>"));
        assertNull(IsbnSuggestionService.plainText("<br/>"));
    }
}

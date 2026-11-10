package br.com.biblioteca.acervo;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import br.com.biblioteca.acervo.IsbnSuggestion.Match;
import br.com.biblioteca.shared.error.ExternalServiceException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

/**
 * Consulta os provedores em ordem (BrasilAPI, depois Open Library), junta o que cada um trouxe (o primeiro que tem o
 * campo ganha) e casa editora, autores e categorias com o cadastro. Não grava nada e não é transacional: a rede não
 * segura conexão do banco.
 */
@Service
public class IsbnSuggestionService {

    private static final Logger log = LoggerFactory.getLogger(IsbnSuggestionService.class);

    /** Palavras que não distinguem uma editora de outra ("Japorama Editora e Comunicação" = "Japorama"). */
    private static final Set<String> PUBLISHER_NOISE = Set.of("editora", "editoras", "editores", "ed", "ltda", "sa", "s", "a",
            "epp", "eireli", "me", "e", "comunicacao", "comunicacoes", "publicacoes");
    private static final Set<String> NAME_CONNECTORS = Set.of("de", "da", "do", "das", "dos", "e", "di", "van", "von");
    private static final int MAX_AUTHORS = 10;
    private static final int MAX_NEW_CATEGORIES = 5;

    private final List<BookDataSource> sources;
    private final BookRepository books;
    private final AuthorRepository authors;
    private final PublisherRepository publishers;
    private final CategoryRepository categories;

    IsbnSuggestionService(List<BookDataSource> sources, BookRepository books, AuthorRepository authors,
                          PublisherRepository publishers, CategoryRepository categories) {
        this.sources = sources;
        this.books = books;
        this.authors = authors;
        this.publishers = publishers;
        this.categories = categories;
    }

    private record Found(String source, BookData data) {
    }

    public IsbnSuggestion lookup(String rawIsbn) {
        String isbn = Isbn.normalize(rawIsbn);
        List<String> variants = new ArrayList<>(List.of(isbn));
        if (Isbn.toIsbn10(isbn) != null) {
            variants.add(Isbn.toIsbn10(isbn));
        }
        List<Long> existing = books.findIdsByIsbnIn(variants);
        if (!existing.isEmpty()) {
            return IsbnSuggestion.duplicate(isbn, existing.get(0));
        }

        List<Found> found = new ArrayList<>();
        boolean unavailable = false;
        for (BookDataSource source : sources) {
            try {
                source.find(isbn).ifPresent(d -> found.add(new Found(source.name(), d)));
            } catch (ExternalServiceException e) {   // um provedor fora do ar não derruba a busca
                unavailable = true;
                log.warn("Consulta de ISBN em {} falhou: {}", source.name(), e.getMessage());
            }
        }
        if (found.isEmpty()) {
            if (unavailable) {
                throw new ExternalServiceException("Não foi possível consultar as bases de livros agora. Preencha manualmente.");
            }
            throw new ResourceNotFoundException("Nenhuma base de livros conhece o ISBN " + isbn + ". Preencha manualmente.");
        }
        return merge(isbn, found);
    }

    private IsbnSuggestion merge(String isbn, List<Found> found) {
        Map<String, String> from = new LinkedHashMap<>();
        Found withTitle = found.stream().filter(f -> f.data().title() != null).findFirst().orElse(null);
        String title = null;
        if (withTitle != null) {
            from.put("title", withTitle.source());
            title = withTitle.data().subtitle() == null ? withTitle.data().title()
                    : withTitle.data().title() + ": " + withTitle.data().subtitle();
            title = title.length() > 250 ? title.substring(0, 250) : title;
        }
        String edition = pick(found, "edition", from, d -> d.edition() == null || d.edition().length() > 40 ? null : d.edition());
        Integer year = pick(found, "publicationYear", from, d -> d.year() != null && d.year() >= 1000 && d.year() <= 2200 ? d.year() : null);
        String language = pick(found, "language", from, BookData::language);
        String description = pick(found, "description", from, d -> d.description() == null ? null : plainText(d.description()));
        String cover = pick(found, "coverUrl", from, d -> coverUrl(d.coverUrl()));
        String publisher = pick(found, "publisher", from, d -> d.publisher() == null || d.publisher().length() > 150 ? null : d.publisher());
        List<String> authorNames = pick(found, "authors", from, BookData::authors);
        List<String> subjects = pick(found, "categories", from, BookData::subjects);
        return new IsbnSuggestion(isbn, false, null, title, edition, year, language, description, cover,
                matchPublisher(publisher), matchAuthors(authorNames), matchCategories(subjects), from);
    }

    /** O primeiro provedor (na ordem) que tem o campo ganha; registra qual foi. */
    private static <T> T pick(List<Found> found, String field, Map<String, String> from, Function<BookData, T> get) {
        for (Found f : found) {
            T value = get.apply(f.data());
            if (value != null && !(value instanceof Collection<?> c && c.isEmpty()) && !(value instanceof String s && s.isBlank())) {
                from.put(field, f.source());
                return value;
            }
        }
        return null;
    }

    private Match matchPublisher(String name) {
        if (name == null) {
            return null;
        }
        String key = publisherKey(name);
        return publishers.findAll(Sort.by("id")).stream().filter(p -> publisherKey(p.getName()).equals(key)).findFirst()
                .map(p -> new Match(p.getId(), p.getName())).orElse(new Match(null, name));
    }

    private List<Match> matchAuthors(List<String> names) {
        if (names == null) {
            return List.of();
        }
        Map<String, Long> registered = new LinkedHashMap<>();   // chave -> id (autor repetido no cadastro: vale o de menor id)
        Map<String, String> registeredName = new LinkedHashMap<>();
        authors.findAll(Sort.by("id")).forEach(a -> {
            registered.putIfAbsent(authorKey(a.getName()), a.getId());
            registeredName.putIfAbsent(authorKey(a.getName()), a.getName());
        });
        Map<String, Match> result = new LinkedHashMap<>();
        for (String raw : names) {
            String name = prettify(raw);
            String key = authorKey(name);
            if (name.length() > 150 || key.isEmpty() || result.containsKey(key) || result.size() >= MAX_AUTHORS) {
                continue;
            }
            result.put(key, registered.containsKey(key) ? new Match(registered.get(key), registeredName.get(key)) : new Match(null, name));
        }
        return List.copyOf(result.values());
    }

    /** Todos os assuntos que já são categorias do cadastro, mais poucos nomes novos (o resto é ruído em inglês). */
    private List<Match> matchCategories(List<String> subjects) {
        if (subjects == null) {
            return List.of();
        }
        Map<String, Match> registered = new LinkedHashMap<>();
        categories.findAll(Sort.by("id")).forEach(c -> registered.putIfAbsent(plainKey(c.getName()), new Match(c.getId(), c.getName())));
        Set<String> seen = new LinkedHashSet<>();
        List<Match> existing = new ArrayList<>();
        List<Match> created = new ArrayList<>();
        for (String name : subjects) {
            String key = plainKey(name);
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            if (registered.containsKey(key)) {
                existing.add(registered.get(key));
            } else if (created.size() < MAX_NEW_CATEGORIES && name.length() <= 100) {
                created.add(new Match(null, name));
            }
        }
        existing.addAll(created);
        return existing;
    }

    // ---------- normalização (visível para os testes) ----------

    private static String plainKey(String s) {
        String t = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return t.replaceAll("[^a-z0-9]+", " ").trim();
    }

    static String publisherKey(String s) {
        List<String> all = Arrays.asList(plainKey(s).split(" "));
        String kept = all.stream().filter(w -> !PUBLISHER_NOISE.contains(w)).collect(Collectors.joining(" "));
        return kept.isEmpty() ? String.join(" ", all) : kept;
    }

    /** Tokens em ordem alfabética: "Orwell, George" e "George Orwell" são o mesmo autor. */
    static String authorKey(String s) {
        return Arrays.stream(plainKey(s).split(" ")).filter(w -> !w.isEmpty()).sorted().collect(Collectors.joining(" "));
    }

    /** "KATSUHIRO OTOMO" vira "Katsuhiro Otomo"; nome que já tem minúsculas fica como veio. */
    static String prettify(String name) {
        String n = name.trim();
        if (!n.equals(n.toUpperCase(Locale.ROOT)) || n.equals(n.toLowerCase(Locale.ROOT))) {
            return n;
        }
        return Arrays.stream(n.toLowerCase(Locale.ROOT).split(" ")).filter(w -> !w.isEmpty())
                .map(w -> NAME_CONNECTORS.contains(w) ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1))
                .collect(Collectors.joining(" "));
    }

    /** Tira marcação HTML e entidades comuns; quebras de parágrafo viram quebra de linha. */
    static String plainText(String html) {
        String s = html.replaceAll("(?i)<br\\s*/?>|</p>", "\n").replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&amp;", "&");
        s = s.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
        return s.isEmpty() ? null : s;
    }

    /** Só http(s) até 500 caracteres (o endereço vira img src); força https. */
    static String coverUrl(String url) {
        if (url == null) {
            return null;
        }
        String u = url.trim().replaceFirst("(?i)^http://", "https://");
        return u.matches("(?i)^https://\\S+$") && u.length() <= 500 ? u : null;
    }
}

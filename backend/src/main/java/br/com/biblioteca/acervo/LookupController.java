package br.com.biblioteca.acervo;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biblioteca.security.Access;
import jakarta.validation.Valid;

/** Leitura: qualquer usuário autenticado. Escrita: equipe da biblioteca. */
@RestController
@RequestMapping("/api/v1")
public class LookupController {

    private final LookupService service;

    public LookupController(LookupService service) {
        this.service = service;
    }

    @PostMapping("/authors")
    @PreAuthorize(Access.STAFF)
    public ResponseEntity<NameResponse> createAuthor(@Valid @RequestBody NameRequest r) {
        return created("/api/v1/authors/", service.createAuthor(r.name()));
    }

    @GetMapping("/authors")
    public List<NameResponse> authors() {
        return service.listAuthors();
    }

    @PostMapping("/publishers")
    @PreAuthorize(Access.STAFF)
    public ResponseEntity<NameResponse> createPublisher(@Valid @RequestBody NameRequest r) {
        return created("/api/v1/publishers/", service.createPublisher(r.name()));
    }

    @GetMapping("/publishers")
    public List<NameResponse> publishers() {
        return service.listPublishers();
    }

    @PostMapping("/categories")
    @PreAuthorize(Access.STAFF)
    public ResponseEntity<NameResponse> createCategory(@Valid @RequestBody NameRequest r) {
        return created("/api/v1/categories/", service.createCategory(r.name()));
    }

    @GetMapping("/categories")
    public List<NameResponse> categories() {
        return service.listCategories();
    }

    @PutMapping("/authors/{id}")
    @PreAuthorize(Access.STAFF)
    public NameResponse renameAuthor(@PathVariable Long id, @Valid @RequestBody NameRequest r) {
        return service.renameAuthor(id, r.name());
    }

    @PutMapping("/publishers/{id}")
    @PreAuthorize(Access.STAFF)
    public NameResponse renamePublisher(@PathVariable Long id, @Valid @RequestBody NameRequest r) {
        return service.renamePublisher(id, r.name());
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(Access.STAFF)
    public NameResponse renameCategory(@PathVariable Long id, @Valid @RequestBody NameRequest r) {
        return service.renameCategory(id, r.name());
    }

    private static ResponseEntity<NameResponse> created(String base, NameResponse body) {
        return ResponseEntity.created(URI.create(base + body.id())).body(body);
    }
}

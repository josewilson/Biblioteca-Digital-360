package br.com.biblioteca.acervo;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.biblioteca.security.Access;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Consulta ao acervo: qualquer usuário autenticado. Alterações: equipe da biblioteca. */
@RestController
@RequestMapping("/api/v1")
public class BookController {

    private final BookService service;

    public BookController(BookService service) {
        this.service = service;
    }

    @PostMapping("/books")
    @PreAuthorize(Access.STAFF)
    public ResponseEntity<BookResponse> create(@Valid @RequestBody CreateBookRequest request,
                                               Authentication auth, HttpServletRequest http) {
        BookResponse created = service.create(request, Long.valueOf(auth.getName()), http.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/books/" + created.id())).body(created);
    }

    @GetMapping("/books")
    public Page<BookResponse> search(@RequestParam(required = false) String q, Pageable pageable) {
        return service.search(q, pageable);
    }

    @PutMapping("/books/{id}")
    @PreAuthorize(Access.STAFF)
    public BookResponse update(@PathVariable Long id, @Valid @RequestBody UpdateBookRequest request,
                               Authentication auth, HttpServletRequest http) {
        return service.update(id, request, Long.valueOf(auth.getName()), http.getRemoteAddr());
    }

    @PutMapping("/copies/{id}")
    @PreAuthorize(Access.STAFF)
    public CopyResponse updateCopy(@PathVariable Long id, @Valid @RequestBody UpdateCopyRequest request,
                                   Authentication auth, HttpServletRequest http) {
        return service.updateCopy(id, request, Long.valueOf(auth.getName()), http.getRemoteAddr());
    }

    @GetMapping("/books/{id}")
    public BookResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/books/{id}/copies")
    @PreAuthorize(Access.STAFF)
    public ResponseEntity<CopyResponse> addCopy(@PathVariable Long id, @Valid @RequestBody CopyRequest request,
                                                Authentication auth, HttpServletRequest http) {
        CopyResponse created = service.addCopy(id, request, Long.valueOf(auth.getName()), http.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/books/" + id)).body(created);
    }

    @PatchMapping("/copies/{id}/status")
    @PreAuthorize(Access.STAFF)
    public CopyResponse changeStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request,
                                     Authentication auth, HttpServletRequest http) {
        return service.changeStatus(id, request.status(), Long.valueOf(auth.getName()), http.getRemoteAddr());
    }
}

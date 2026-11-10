package br.com.biblioteca.acervo;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biblioteca.security.Access;

/** Sugestão de cadastro por ISBN para o formulário "Nova obra". Só lê de fora e do acervo; não grava nada. */
@RestController
@RequestMapping("/api/v1/books/isbn")
public class IsbnLookupController {

    private final IsbnSuggestionService service;

    public IsbnLookupController(IsbnSuggestionService service) {
        this.service = service;
    }

    @GetMapping("/{isbn}")
    @PreAuthorize(Access.STAFF)
    public IsbnSuggestion lookup(@PathVariable String isbn) {
        return service.lookup(isbn);
    }
}

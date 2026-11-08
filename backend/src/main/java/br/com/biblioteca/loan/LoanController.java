package br.com.biblioteca.loan;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.biblioteca.security.Access;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Balcão: a equipe opera empréstimos. Cada leitor vê só os próprios em /me/loans. */
@RestController
@RequestMapping("/api/v1")
public class LoanController {

    private final LoanService service;

    public LoanController(LoanService service) {
        this.service = service;
    }

    @PostMapping("/loans")
    @PreAuthorize(Access.STAFF)
    public ResponseEntity<LoanResponse> lend(@Valid @RequestBody LendRequest request, Authentication auth,
                                             HttpServletRequest http) {
        LoanResponse created = service.lend(request, Long.valueOf(auth.getName()), http.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/loans/" + created.id())).body(created);
    }

    @PostMapping("/loans/{id}/return")
    @PreAuthorize(Access.STAFF)
    public ReturnResponse giveBack(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return service.giveBack(id, Long.valueOf(auth.getName()), http.getRemoteAddr());
    }

    @PostMapping("/loans/{id}/renew")
    @PreAuthorize(Access.STAFF)
    public LoanResponse renew(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return service.renew(id, Long.valueOf(auth.getName()), http.getRemoteAddr());
    }

    @GetMapping("/loans")
    @PreAuthorize(Access.STAFF)
    public Page<LoanResponse> list(@RequestParam(required = false) Long userId,
                                   @RequestParam(defaultValue = "false") boolean activeOnly, Pageable pageable) {
        return service.list(userId, activeOnly, pageable);
    }

    @GetMapping("/loans/{id}")
    @PreAuthorize(Access.STAFF)
    public LoanResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @GetMapping("/me/loans")
    public Page<LoanResponse> mine(Authentication auth, @RequestParam(defaultValue = "false") boolean activeOnly,
                                   Pageable pageable) {
        return service.list(Long.valueOf(auth.getName()), activeOnly, pageable);
    }
}

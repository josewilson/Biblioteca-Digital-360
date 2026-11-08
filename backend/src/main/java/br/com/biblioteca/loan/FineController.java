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

/** Multas: a equipe recebe pagamentos; só administradores e bibliotecário isentam. Cada leitor vê as próprias. */
@RestController
@RequestMapping("/api/v1")
public class FineController {

    private final FineService service;

    public FineController(FineService service) {
        this.service = service;
    }

    @GetMapping("/fines")
    @PreAuthorize(Access.STAFF)
    public Page<FineResponse> list(@RequestParam(required = false) Long userId,
                                   @RequestParam(required = false) FineStatus status, Pageable pageable) {
        return service.list(userId, status, pageable);
    }

    @PostMapping("/fines/{id}/payments")
    @PreAuthorize(Access.STAFF)
    public ResponseEntity<PaymentResponse> pay(@PathVariable Long id, @Valid @RequestBody PayRequest request,
                                               Authentication auth, HttpServletRequest http) {
        PaymentResponse payment = service.pay(id, request.method(), Long.valueOf(auth.getName()), http.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.id())).body(payment);
    }

    @PostMapping("/fines/{id}/waive")
    @PreAuthorize(Access.WAIVER)
    public FineResponse waive(@PathVariable Long id, @Valid @RequestBody WaiveRequest request,
                              Authentication auth, HttpServletRequest http) {
        return service.waive(id, request.reason(), Long.valueOf(auth.getName()), http.getRemoteAddr());
    }

    @GetMapping("/payments/{id}")
    @PreAuthorize(Access.STAFF)
    public PaymentResponse receipt(@PathVariable Long id) {
        return service.receipt(id);
    }

    @GetMapping("/me/fines")
    public Page<FineResponse> mine(Authentication auth, @RequestParam(required = false) FineStatus status,
                                   Pageable pageable) {
        return service.list(Long.valueOf(auth.getName()), status, pageable);
    }
}

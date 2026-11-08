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

/** A equipe opera reservas de qualquer leitor; cada leitor reserva e cancela as próprias em /me/reservations. */
@RestController
@RequestMapping("/api/v1")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @PostMapping("/reservations")
    @PreAuthorize(Access.STAFF)
    public ResponseEntity<ReservationResponse> reserveFor(@Valid @RequestBody ReserveForRequest request,
                                                          Authentication auth, HttpServletRequest http) {
        return created(service.reserve(request.userId(), request.bookId(), id(auth), http.getRemoteAddr()));
    }

    @PostMapping("/reservations/{id}/cancel")
    @PreAuthorize(Access.STAFF)
    public ReservationResponse cancel(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return service.cancel(id, id(auth), true, http.getRemoteAddr());
    }

    @GetMapping("/reservations")
    @PreAuthorize(Access.STAFF)
    public Page<ReservationResponse> list(@RequestParam(required = false) Long userId,
                                          @RequestParam(required = false) ReservationStatus status,
                                          Pageable pageable) {
        return service.list(userId, status, pageable);
    }

    @PostMapping("/me/reservations")
    public ResponseEntity<ReservationResponse> reserve(@Valid @RequestBody ReserveRequest request,
                                                       Authentication auth, HttpServletRequest http) {
        return created(service.reserve(id(auth), request.bookId(), id(auth), http.getRemoteAddr()));
    }

    @PostMapping("/me/reservations/{id}/cancel")
    public ReservationResponse cancelMine(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return service.cancel(id, id(auth), false, http.getRemoteAddr());
    }

    @GetMapping("/me/reservations")
    public Page<ReservationResponse> mine(Authentication auth,
                                          @RequestParam(required = false) ReservationStatus status,
                                          Pageable pageable) {
        return service.list(id(auth), status, pageable);
    }

    private static Long id(Authentication auth) {
        return Long.valueOf(auth.getName());
    }

    private static ResponseEntity<ReservationResponse> created(ReservationResponse body) {
        return ResponseEntity.created(URI.create("/api/v1/reservations/" + body.id())).body(body);
    }
}

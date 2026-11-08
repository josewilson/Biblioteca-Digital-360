package br.com.biblioteca.loan;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.acervo.Book;
import br.com.biblioteca.acervo.BookCopy;
import br.com.biblioteca.acervo.BookCopyRepository;
import br.com.biblioteca.acervo.BookRepository;
import br.com.biblioteca.acervo.CopyBecameAvailable;
import br.com.biblioteca.acervo.CopyKind;
import br.com.biblioteca.acervo.CopyStatus;
import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.notification.NotificationService;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;
import br.com.biblioteca.user.User;
import br.com.biblioteca.user.UserRepository;

/**
 * Fila de reservas. Quando um exemplar emprestável fica livre, ele é guardado (RESERVADO) para o 1º da fila,
 * com prazo para retirada; passado o prazo, vai para o próximo.
 *
 * Ordem de travas (evita deadlock): leitor -> exemplar -> reserva.
 */
@Service
public class ReservationService {

    private static final List<ReservationStatus> OPEN = List.of(ReservationStatus.NA_FILA, ReservationStatus.DISPONIVEL);

    private final ReservationRepository reservations;
    private final BookCopyRepository copies;
    private final BookRepository books;
    private final UserRepository users;
    private final BorrowerPolicies policies;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    ReservationService(ReservationRepository reservations, BookCopyRepository copies, BookRepository books,
                       UserRepository users, BorrowerPolicies policies, NotificationService notifications,
                       AuditService audit, Clock clock) {
        this.reservations = reservations;
        this.copies = copies;
        this.books = books;
        this.users = users;
        this.policies = policies;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ReservationResponse reserve(Long userId, Long bookId, Long callerId, String ip) {
        User user = users.findForUpdateById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Leitor " + userId + " não existe."));
        if (!user.isActive()) {
            throw new BusinessRuleException("Leitor inativo.");
        }
        policies.policyFor(user);   // só quem pode pegar livros emprestados pode reservar
        Book book = books.findById(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Obra " + bookId + " não existe."));
        if (reservations.existsByUserIdAndBookIdAndStatusIn(userId, bookId, OPEN)) {
            throw new BusinessRuleException("Você já tem uma reserva aberta para esta obra.");
        }
        if (copies.existsByBookIdAndKindAndStatus(bookId, CopyKind.CIRCULANTE, CopyStatus.DISPONIVEL)) {
            throw new BusinessRuleException("Há exemplar disponível: faça o empréstimo direto, sem reserva.");
        }
        Reservation reservation = reservations.save(new Reservation(book, userId, clock.instant()));
        audit.record(callerId, "RESERVATION_CREATED", "reservation", reservation.getId(), "obra=" + bookId, ip);
        return toResponse(reservation);
    }

    /** Cancela uma reserva aberta. Quem não é da equipe só cancela a própria (senão: 404, sem revelar que existe). */
    @Transactional
    public ReservationResponse cancel(Long reservationId, Long requesterId, boolean staff, String ip) {
        Reservation reservation = lockWithItsCopy(reservationId);
        if (!staff && !reservation.getUserId().equals(requesterId)) {
            throw new ResourceNotFoundException("Reserva " + reservationId + " não existe.");
        }
        if (!OPEN.contains(reservation.getStatus())) {
            throw new BusinessRuleException("Esta reserva não pode mais ser cancelada.");
        }
        boolean wasHoldingCopy = reservation.getStatus() == ReservationStatus.DISPONIVEL;
        reservation.markCancelled();
        if (wasHoldingCopy) {
            reservation.getCopy().release();
            serveQueue(reservation.getBook().getId());   // o exemplar liberado vai para o próximo
        }
        audit.record(requesterId, "RESERVATION_CANCELLED", "reservation", reservationId, null, ip);
        return ReservationResponse.from(reservation, null);
    }

    /** Guarda exemplares livres para quem está na fila, na ordem. Chamado ao devolver e quando surge exemplar novo. */
    @Transactional
    public void serveQueue(Long bookId) {
        while (true) {
            Reservation next = reservations
                    .findFirstByBookIdAndStatusOrderByIdAsc(bookId, ReservationStatus.NA_FILA).orElse(null);
            if (next == null) {
                return;
            }
            BookCopy free = copies.findFirstByBookIdAndKindAndStatusOrderByIdAsc(
                    bookId, CopyKind.CIRCULANTE, CopyStatus.DISPONIVEL).orElse(null);
            if (free == null) {
                return;
            }
            User user = users.findById(next.getUserId()).orElse(null);
            int pickupDays;
            try {
                if (user == null || !user.isActive()) {
                    throw new BusinessRuleException("Leitor indisponível.");
                }
                pickupDays = policies.policyFor(user).getReservationPickupDays();
            } catch (BusinessRuleException e) {
                // Leitor que não pode mais emprestar não trava a fila (nem derruba a devolução em andamento).
                next.markCancelled();
                continue;
            }
            free.hold();
            next.holdCopy(free, clock.instant().atZone(clock.getZone()).plusDays(pickupDays).toInstant());
            notifications.record(next.getUserId(), "RESERVATION_AVAILABLE", "\"" + next.getBook().getTitle()
                    + "\" está disponível para retirada até " + next.getExpiresAt() + ".");
        }
    }

    @EventListener
    public void onCopyAvailable(CopyBecameAvailable event) {
        serveQueue(event.bookId());
    }

    /**
     * Retirada de um exemplar guardado: só o leitor da reserva, dentro do prazo.
     * Chamado por LoanService com o exemplar já travado.
     */
    @Transactional
    public void claimHeldCopy(BookCopy copy, Long userId) {
        Reservation reservation = reservations.findByCopyIdAndStatus(copy.getId(), ReservationStatus.DISPONIVEL)
                .orElseThrow(() -> new BusinessRuleException("Exemplar não está disponível (situação: RESERVADO)."));
        if (!reservation.getUserId().equals(userId)) {
            throw new BusinessRuleException("Exemplar reservado para outro leitor.");
        }
        if (reservation.getExpiresAt().isBefore(clock.instant())) {
            throw new BusinessRuleException("O prazo de retirada desta reserva expirou; o exemplar será liberado em instantes.");
        }
        reservation.markPickedUp();
        copy.release();
    }

    public boolean hasQueue(Long bookId) {
        return reservations.existsByBookIdAndStatus(bookId, ReservationStatus.NA_FILA);
    }

    /** Libera exemplares cuja retirada passou do prazo e atende o próximo da fila. */
    // ponytail: varredura a cada 5 min (a retirada fora do prazo já é recusada na hora); se precisar de precisão, agendar por reserva.
    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT5M")
    @Transactional
    public void expireOverdue() {
        Instant now = clock.instant();
        for (Long id : reservations.findExpiredIds(now)) {
            Reservation reservation = lockWithItsCopy(id);
            if (reservation.getStatus() != ReservationStatus.DISPONIVEL || reservation.getExpiresAt().isAfter(now)) {
                continue;   // mudou enquanto esperávamos a trava (retirada ou cancelada)
            }
            reservation.markExpired();
            reservation.getCopy().release();
            notifications.record(reservation.getUserId(), "RESERVATION_EXPIRED",
                    "Sua reserva de \"" + reservation.getBook().getTitle() + "\" expirou.");
            serveQueue(reservation.getBook().getId());
        }
    }

    @Transactional(readOnly = true)
    public Page<ReservationResponse> list(Long userId, ReservationStatus status, Pageable pageable) {
        Page<Reservation> page;
        if (userId != null) {
            page = status == null ? reservations.findByUserId(userId, pageable)
                    : reservations.findByUserIdAndStatus(userId, status, pageable);
        } else {
            page = status == null ? reservations.findAll(pageable) : reservations.findByStatus(status, pageable);
        }
        return page.map(this::toResponse);   // ponytail: uma consulta de posição por item da página (páginas pequenas)
    }

    private ReservationResponse toResponse(Reservation r) {
        Integer position = r.getStatus() == ReservationStatus.NA_FILA
                ? (int) reservations.countAhead(r.getBook().getId(), r.getId()) + 1 : null;
        return ReservationResponse.from(r, position);
    }

    /** Trava o exemplar guardado ANTES da reserva (mesma ordem de LoanService.lend), depois relê a reserva. */
    private Reservation lockWithItsCopy(Long reservationId) {
        reservations.findCopyIdById(reservationId).ifPresent(copies::findForUpdateById);
        return reservations.findForUpdateById(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException("Reserva " + reservationId + " não existe."));
    }
}

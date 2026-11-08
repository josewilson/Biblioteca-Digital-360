package br.com.biblioteca.loan;

import java.time.Instant;

/** {@code queuePosition} só existe enquanto a reserva está NA_FILA (1 = próxima). */
public record ReservationResponse(Long id, Long bookId, String bookTitle, Long userId, ReservationStatus status,
                                  Integer queuePosition, Long copyId, Instant queuedAt, Instant expiresAt) {

    static ReservationResponse from(Reservation r, Integer queuePosition) {
        return new ReservationResponse(r.getId(), r.getBook().getId(), r.getBook().getTitle(), r.getUserId(),
                r.getStatus(), queuePosition, r.getCopy() == null ? null : r.getCopy().getId(), r.getQueuedAt(),
                r.getExpiresAt());
    }
}

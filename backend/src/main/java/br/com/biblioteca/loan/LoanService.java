package br.com.biblioteca.loan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.acervo.BookCopy;
import br.com.biblioteca.acervo.BookCopyRepository;
import br.com.biblioteca.acervo.CopyStatus;
import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.notification.NotificationService;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;
import br.com.biblioteca.user.User;
import br.com.biblioteca.user.UserRepository;

/**
 * Empréstimo, devolução e renovação. Cada operação é uma transação que trava as linhas envolvidas
 * (leitor, exemplar ou empréstimo), então operações simultâneas não furam o limite nem emprestam duas vezes.
 * Ordem de travas: leitor -> exemplar -> reserva.
 */
@Service
public class LoanService {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LoanRepository loans;
    private final LoanRenewalRepository renewals;
    private final FineRepository fines;
    private final BorrowerPolicies policies;
    private final UserRepository users;
    private final BookCopyRepository copies;
    private final ReservationService reservations;
    private final NotificationService notifications;
    private final AuditService audit;
    private final Clock clock;

    LoanService(LoanRepository loans, LoanRenewalRepository renewals, FineRepository fines,
                BorrowerPolicies policies, UserRepository users, BookCopyRepository copies,
                ReservationService reservations, NotificationService notifications, AuditService audit,
                Clock clock) {
        this.loans = loans;
        this.renewals = renewals;
        this.fines = fines;
        this.policies = policies;
        this.users = users;
        this.copies = copies;
        this.reservations = reservations;
        this.notifications = notifications;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public LoanResponse lend(LendRequest req, Long callerId, String ip) {
        User borrower = users.findForUpdateById(req.userId())
                .orElseThrow(() -> new ResourceNotFoundException("Leitor " + req.userId() + " não existe."));
        if (!borrower.isActive()) {
            throw new BusinessRuleException("Leitor inativo.");
        }
        LoanPolicy policy = policies.policyFor(borrower);
        Long userId = borrower.getId();
        Instant now = clock.instant();

        if (loans.countByUserIdAndReturnedAtIsNull(userId) >= policy.getMaxLoans()) {
            throw new BusinessRuleException(
                    "Limite de " + policy.getMaxLoans() + " empréstimos simultâneos atingido.");
        }
        if (loans.existsByUserIdAndReturnedAtIsNullAndDueAtBefore(userId, now)) {
            throw new BusinessRuleException("Leitor com empréstimo atrasado: devolva antes de pegar outro livro.");
        }
        if (fines.existsOpenByUserId(userId)) {
            throw new BusinessRuleException("Leitor com multa em aberto.");
        }

        BookCopy copy = copies.findForUpdateById(req.copyId())
                .orElseThrow(() -> new ResourceNotFoundException("Exemplar " + req.copyId() + " não existe."));
        if (copy.getStatus() == CopyStatus.RESERVADO) {
            reservations.claimHeldCopy(copy, userId);   // só o dono da reserva retira, dentro do prazo
        }
        copy.lend();   // recusa consulta e exemplar indisponível

        Instant due = plusDays(now, policy.getLoanDays());
        Loan loan = loans.save(new Loan(userId, copy, callerId, now, due));
        audit.record(callerId, "LOAN_CREATED", "loan", loan.getId(), "leitor=" + userId + " exemplar=" + copy.getId(), ip);
        notifications.record(userId, "LOAN_CONFIRMED", "Empréstimo de \"" + copy.getBook().getTitle()
                + "\" confirmado. Devolva até " + DATE.format(due.atZone(clock.getZone())) + ".");
        return LoanResponse.from(loan);
    }

    @Transactional
    public ReturnResponse giveBack(Long loanId, Long callerId, String ip) {
        Loan loan = lockedLoan(loanId);
        if (!loan.isActive()) {
            throw new BusinessRuleException("Este empréstimo já foi devolvido.");
        }
        loan.getCopy().giveBack();
        loan.markReturned(clock.instant());

        Fine fine = chargeFineIfLate(loan);
        reservations.serveQueue(loan.getCopy().getBook().getId());   // exemplar devolvido vai para o 1º da fila
        audit.record(callerId, "LOAN_RETURNED", "loan", loanId, fine == null ? null : "multa=" + fine.getAmount(), ip);
        return new ReturnResponse(LoanResponse.from(loan), fine == null ? null : FineResponse.from(fine));
    }

    @Transactional
    public LoanResponse renew(Long loanId, Long callerId, String ip) {
        Loan loan = lockedLoan(loanId);
        if (!loan.isActive()) {
            throw new BusinessRuleException("Este empréstimo já foi devolvido.");
        }
        if (!loan.getDueAt().isAfter(clock.instant())) {
            throw new BusinessRuleException("Empréstimo atrasado não pode ser renovado: devolva o livro.");
        }
        if (reservations.hasQueue(loan.getCopy().getBook().getId())) {
            throw new BusinessRuleException("Há leitores na fila de reservas desta obra: não é possível renovar.");
        }
        LoanPolicy policy = policies.policyFor(borrowerOf(loan));
        if (renewals.countByLoanId(loanId) >= policy.getMaxRenewals()) {
            throw new BusinessRuleException("Limite de " + policy.getMaxRenewals() + " renovações atingido.");
        }
        Instant newDue = plusDays(loan.getDueAt(), policy.getRenewalDays());
        renewals.save(new LoanRenewal(loan, loan.getDueAt(), newDue, callerId));
        loan.extendTo(newDue);
        audit.record(callerId, "LOAN_RENEWED", "loan", loanId, "novo prazo=" + newDue, ip);
        return LoanResponse.from(loan);
    }

    @Transactional(readOnly = true)
    public Page<LoanResponse> list(Long userId, boolean activeOnly, Pageable pageable) {
        Page<Loan> page;
        if (userId != null) {
            page = activeOnly ? loans.findByUserIdAndReturnedAtIsNull(userId, pageable)
                    : loans.findByUserId(userId, pageable);
        } else {
            page = activeOnly ? loans.findByReturnedAtIsNull(pageable) : loans.findAll(pageable);
        }
        return page.map(LoanResponse::from);
    }

    @Transactional(readOnly = true)
    public LoanResponse get(Long id) {
        return LoanResponse.from(loans.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empréstimo " + id + " não existe.")));
    }

    /** Multa = dias corridos de atraso (pela data na zona da aplicação) x valor do perfil. Sem taxa, sem multa. */
    private Fine chargeFineIfLate(Loan loan) {
        LocalDate dueDay = loan.getDueAt().atZone(clock.getZone()).toLocalDate();
        long daysLate = ChronoUnit.DAYS.between(dueDay, LocalDate.now(clock));
        if (daysLate <= 0) {
            return null;
        }
        User borrower = borrowerOf(loan);
        BigDecimal amount = policies.dailyFineRate(borrower)
                .multiply(BigDecimal.valueOf(daysLate)).setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            return null;
        }
        Fine fine = fines.save(new Fine(loan, amount, (int) daysLate));
        notifications.record(borrower.getId(), "FINE_REGISTERED", String.format(PT_BR,
                "Multa de R$ %.2f por %d dia(s) de atraso na devolução de \"%s\".",
                amount, daysLate, loan.getCopy().getBook().getTitle()));
        return fine;
    }

    private User borrowerOf(Loan loan) {
        return users.findById(loan.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Leitor " + loan.getUserId() + " não existe."));
    }

    private Loan lockedLoan(Long id) {
        return loans.findForUpdateById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Empréstimo " + id + " não existe."));
    }

    private Instant plusDays(Instant from, int days) {
        return from.atZone(clock.getZone()).plusDays(days).toInstant();
    }
}

package br.com.biblioteca.loan;

import br.com.biblioteca.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Limite, prazo e renovações de um perfil (tabela loan_limits). Perfil sem linha não pode emprestar. */
@Entity
@Table(name = "loan_limits")
class LoanPolicy {

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    private int maxLoans;
    private int loanDays;
    private int maxRenewals;
    private int renewalDays;
    private int reservationPickupDays;

    protected LoanPolicy() {
    }

    /** Para testes. */
    LoanPolicy(int maxLoans, int loanDays, int maxRenewals, int renewalDays) {
        this(maxLoans, loanDays, maxRenewals, renewalDays, 3);
    }

    LoanPolicy(int maxLoans, int loanDays, int maxRenewals, int renewalDays, int reservationPickupDays) {
        this.maxLoans = maxLoans;
        this.loanDays = loanDays;
        this.maxRenewals = maxRenewals;
        this.renewalDays = renewalDays;
        this.reservationPickupDays = reservationPickupDays;
    }

    int getReservationPickupDays() {
        return reservationPickupDays;
    }

    int getMaxLoans() {
        return maxLoans;
    }

    int getLoanDays() {
        return loanDays;
    }

    int getMaxRenewals() {
        return maxRenewals;
    }

    int getRenewalDays() {
        return renewalDays;
    }
}

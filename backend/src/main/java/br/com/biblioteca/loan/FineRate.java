package br.com.biblioteca.loan;

import java.math.BigDecimal;

import br.com.biblioteca.user.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** Multa por dia de atraso de um perfil, em reais. Perfil sem linha não paga multa. */
@Entity
@Table(name = "fine_rates")
class FineRate {

    @Id
    @Column(name = "role_id")
    private Long roleId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", insertable = false, updatable = false)
    private Role role;

    private BigDecimal dailyRate;

    protected FineRate() {
    }

    /** Para testes. */
    FineRate(BigDecimal dailyRate) {
        this.dailyRate = dailyRate;
    }

    BigDecimal getDailyRate() {
        return dailyRate;
    }
}

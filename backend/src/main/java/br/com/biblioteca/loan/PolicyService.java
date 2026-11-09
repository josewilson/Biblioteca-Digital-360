package br.com.biblioteca.loan;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

/** Regras de empréstimo e multa por perfil. Só perfis que já emprestam têm regra: visitante continua sem empréstimo. */
@Service
public class PolicyService {

    private final LoanPolicyRepository policies;
    private final FineRateRepository fineRates;
    private final AuditService audit;

    PolicyService(LoanPolicyRepository policies, FineRateRepository fineRates, AuditService audit) {
        this.policies = policies;
        this.fineRates = fineRates;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<PolicyResponse> list() {
        return policies.findAll().stream()
                .map(p -> PolicyResponse.from(p, rateOf(p)))
                .sorted(Comparator.comparing(PolicyResponse::role))
                .toList();
    }

    @Transactional
    public PolicyResponse update(String roleCode, PolicyRequest r, Long callerId, String ip) {
        LoanPolicy policy = policies.findByRoleCodes(Set.of(roleCode)).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Este perfil não tem regra de empréstimo."));
        policy.update(r.maxLoans(), r.loanDays(), r.maxRenewals(), r.renewalDays(), r.reservationPickupDays());

        FineRate rate = fineRates.findById(policy.getRoleId())
                .orElseGet(() -> new FineRate(policy.getRoleId(), r.dailyRate()));
        rate.setDailyRate(r.dailyRate());
        fineRates.save(rate);

        audit.record(callerId, "POLICY_UPDATED", "role", policy.getRoleId(), roleCode + ": " + r, ip);
        return PolicyResponse.from(policy, r.dailyRate());
    }

    private BigDecimal rateOf(LoanPolicy p) {
        return fineRates.findById(p.getRoleId()).map(FineRate::getDailyRate).orElse(BigDecimal.ZERO);
    }
}

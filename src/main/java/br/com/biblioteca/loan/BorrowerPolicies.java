package br.com.biblioteca.loan;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.user.Role;
import br.com.biblioteca.user.User;

/**
 * Regras configuráveis de um leitor (limite, prazos, multa por dia), lidas das tabelas da administração.
 * Quem tem vários perfis recebe a política mais favorável (maior limite) e a maior multa por dia.
 */
@Component
class BorrowerPolicies {

    private final LoanPolicyRepository policies;
    private final FineRateRepository fineRates;

    BorrowerPolicies(LoanPolicyRepository policies, FineRateRepository fineRates) {
        this.policies = policies;
        this.fineRates = fineRates;
    }

    /** @throws BusinessRuleException se nenhum perfil do leitor pode pegar livros emprestados (ex.: visitante) */
    LoanPolicy policyFor(User user) {
        return policies.findByRoleCodes(roleCodes(user)).stream()
                .max(Comparator.comparingInt(LoanPolicy::getMaxLoans))
                .orElseThrow(() -> new BusinessRuleException(
                        "Este perfil não pode pegar livros emprestados nem reservar (apenas consulta no local)."));
    }

    /** Multa por dia; zero se nenhum perfil do leitor tiver valor configurado. */
    BigDecimal dailyFineRate(User user) {
        return fineRates.findByRoleCodes(roleCodes(user)).stream()
                .map(FineRate::getDailyRate).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
    }

    private Set<String> roleCodes(User user) {
        return user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
    }
}

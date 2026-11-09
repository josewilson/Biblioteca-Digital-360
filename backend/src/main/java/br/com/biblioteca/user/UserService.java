package br.com.biblioteca.user;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.SearchText;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

@Service
public class UserService {

    private static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "ADMIN_INSTITUTION");
    /** Perfis de equipe: só administradores podem atribuí-los. */
    private static final Set<String> STAFF_ROLES = Set.of("SUPER_ADMIN", "ADMIN_INSTITUTION", "LIBRARIAN", "ASSISTANT");
    private static final int MAX_LOOKUP = 100;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.audit = audit;
    }

    /**
     * @param callerRoles perfis de quem está cadastrando; impede escalada de privilégio
     *                    (bibliotecário não cria administrador, só SUPER_ADMIN cria SUPER_ADMIN).
     */
    @Transactional
    public UserResponse create(CreateUserRequest req, Set<String> callerRoles, Long callerId, String ip) {
        checkCanAssign(req.roles(), callerRoles);

        String email = req.email().trim().toLowerCase();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Já existe usuário com este e-mail.");
        }
        if (req.cpf() != null && users.existsByCpf(req.cpf())) {
            throw new BusinessRuleException("Já existe usuário com este CPF.");
        }
        if (req.registration() != null && users.existsByRegistration(req.registration())) {
            throw new BusinessRuleException("Já existe usuário com esta matrícula.");
        }
        Set<Role> found = roles.findByCodeIn(req.roles());
        if (found.size() != req.roles().size()) {
            throw new BusinessRuleException("Perfil inexistente.");
        }

        User user = new User(req.fullName().trim(), email, encoder.encode(req.password()), found);
        user.setCpf(req.cpf());
        user.setRegistration(req.registration());
        user.setPhone(req.phone());
        user.setAddress(req.address());
        user.requirePasswordChange();   // a senha inicial é conhecida por quem cadastrou
        users.save(user);
        audit.record(callerId, "USER_CREATED", "user", user.getId(), "perfis=" + req.roles(), ip);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(String query, Pageable pageable) {
        Page<User> page = (query == null || query.isBlank())
                ? users.findAll(pageable)
                : users.search(SearchText.contains(query), pageable);
        return page.map(UserResponse::from);
    }

    /** Vários usuários por id, para as telas mostrarem nomes em vez de números. */
    @Transactional(readOnly = true)
    public List<UserResponse> lookup(List<Long> ids) {
        if (ids.size() > MAX_LOOKUP) {
            throw new BusinessRuleException("Peça no máximo " + MAX_LOOKUP + " usuários por vez.");
        }
        return users.findAllById(ids).stream().map(UserResponse::from).toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest req, Set<String> callerRoles, Long callerId, String ip) {
        User user = find(id);
        checkCanAssign(roleCodes(user), callerRoles);   // não mexe em quem está acima de você
        checkCanAssign(req.roles(), callerRoles);

        String email = req.email().trim().toLowerCase();
        String registration = blankToNull(req.registration());
        if (users.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new BusinessRuleException("Já existe usuário com este e-mail.");
        }
        if (registration != null && users.existsByRegistrationAndIdNot(registration, id)) {
            throw new BusinessRuleException("Já existe usuário com esta matrícula.");
        }
        if (req.cpf() != null && !req.cpf().isEmpty() && users.existsByCpfAndIdNot(req.cpf(), id)) {
            throw new BusinessRuleException("Já existe usuário com este CPF.");
        }

        boolean rolesChanged = !roleCodes(user).equals(req.roles());
        if (rolesChanged && id.equals(callerId)) {
            throw new BusinessRuleException("Você não pode alterar os próprios perfis.");
        }
        if (rolesChanged) {
            Set<Role> found = roles.findByCodeIn(req.roles());
            if (found.size() != req.roles().size()) {
                throw new BusinessRuleException("Perfil inexistente.");
            }
            user.replaceRoles(found);
        }
        user.rename(req.fullName().trim(), email);
        user.setRegistration(registration);
        user.setPhone(blankToNull(req.phone()));
        if (req.cpf() != null) {
            user.setCpf(blankToNull(req.cpf()));
        }
        audit.record(callerId, "USER_UPDATED", "user", id, rolesChanged ? "perfis=" + req.roles() : null, ip);
        return UserResponse.from(user);
    }

    /** A equipe define uma senha temporária: o dono é obrigado a trocá-la na próxima entrada. */
    @Transactional
    public void resetPassword(Long id, String newPassword, Set<String> callerRoles, Long callerId, String ip) {
        User user = find(id);
        checkCanAssign(roleCodes(user), callerRoles);
        user.changePassword(encoder.encode(newPassword), true);
        audit.record(callerId, "PASSWORD_RESET", "user", id, null, ip);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public void deactivate(Long id, Set<String> callerRoles, Long callerId, String ip) {
        if (id.equals(callerId)) {
            throw new BusinessRuleException("Você não pode desativar a própria conta.");
        }
        User user = find(id);
        checkCanAssign(roleCodes(user), callerRoles);
        user.deactivate();
        audit.record(callerId, "USER_DEACTIVATED", "user", id, null, ip);
    }

    @Transactional
    public UserResponse reactivate(Long id, Set<String> callerRoles, Long callerId, String ip) {
        User user = find(id);
        checkCanAssign(roleCodes(user), callerRoles);
        user.reactivate();
        audit.record(callerId, "USER_REACTIVATED", "user", id, null, ip);
        return UserResponse.from(user);
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário " + id + " não existe."));
    }

    private static Set<String> roleCodes(User user) {
        return user.getRoles().stream().map(Role::getCode).collect(Collectors.toSet());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void checkCanAssign(Set<String> wanted, Set<String> callerRoles) {
        boolean callerIsAdmin = callerRoles.stream().anyMatch(ADMIN_ROLES::contains);
        for (String role : wanted) {
            boolean allowed = switch (role) {
                case "SUPER_ADMIN" -> callerRoles.contains("SUPER_ADMIN");
                case "ADMIN_INSTITUTION", "LIBRARIAN", "ASSISTANT" -> callerIsAdmin;
                default -> true;
            };
            if (!allowed) {
                throw new BusinessRuleException("Você não pode atribuir nem administrar o perfil " + role + ".");
            }
        }
    }
}

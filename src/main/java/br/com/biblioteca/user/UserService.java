package br.com.biblioteca.user;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

@Service
public class UserService {

    private static final Set<String> ADMIN_ROLES = Set.of("SUPER_ADMIN", "ADMIN_INSTITUTION");
    /** Perfis de equipe: só administradores podem atribuí-los. */
    private static final Set<String> STAFF_ROLES = Set.of("SUPER_ADMIN", "ADMIN_INSTITUTION", "LIBRARIAN", "ASSISTANT");

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
        users.save(user);
        audit.record(callerId, "USER_CREATED", "user", user.getId(), "perfis=" + req.roles(), ip);
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(Pageable pageable) {
        return users.findAll(pageable).map(UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(Long id) {
        return UserResponse.from(find(id));
    }

    @Transactional
    public void deactivate(Long id, Long callerId, String ip) {
        User user = find(id);
        user.deactivate();
        audit.record(callerId, "USER_DEACTIVATED", "user", id, null, ip);
    }

    private User find(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Usuário " + id + " não existe."));
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
                throw new BusinessRuleException("Você não pode atribuir o perfil " + role + ".");
            }
        }
    }
}

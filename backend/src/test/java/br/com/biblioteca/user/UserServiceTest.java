package br.com.biblioteca.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import br.com.biblioteca.audit.AuditService;
import br.com.biblioteca.shared.error.BusinessRuleException;

class UserServiceTest {

    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final UserService service =
            new UserService(users, roles, new BCryptPasswordEncoder(), mock(AuditService.class), mock(PersonalDataStore.class));

    private CreateUserRequest request(String email, String... roleCodes) {
        return new CreateUserRequest("Ana Souza", email, "senha-forte-1", null, null, null, null, Set.of(roleCodes));
    }

    private Role role(String code) {
        Role r = mock(Role.class);
        when(r.getCode()).thenReturn(code);
        return r;
    }

    @Test
    void guardaASenhaComHashENuncaEmTextoPuro() {
        Role student = role("STUDENT");
        when(roles.findByCodeIn(any())).thenReturn(Set.of(student));

        service.create(request("Ana@X.com", "STUDENT"), Set.of("LIBRARIAN"), 1L, "127.0.0.1");

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertEquals("ana@x.com", saved.getValue().getEmail());
        assertNotEquals("senha-forte-1", saved.getValue().getPasswordHash());
    }

    @Test
    void recusaEmailRepetido() {
        when(users.existsByEmailIgnoreCase("ana@x.com")).thenReturn(true);
        assertThrows(BusinessRuleException.class,
                () -> service.create(request("ana@x.com", "STUDENT"), Set.of("LIBRARIAN"), 1L, "ip"));
    }

    @Test
    void bibliotecarioNaoCriaAdministradorNemOutroBibliotecario() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(request("a@x.com", "ADMIN_INSTITUTION"), Set.of("LIBRARIAN"), 1L, "ip"));
        assertThrows(BusinessRuleException.class,
                () -> service.create(request("b@x.com", "LIBRARIAN"), Set.of("LIBRARIAN"), 1L, "ip"));
    }

    @Test
    void soSuperAdminCriaSuperAdmin() {
        assertThrows(BusinessRuleException.class,
                () -> service.create(request("a@x.com", "SUPER_ADMIN"), Set.of("ADMIN_INSTITUTION"), 1L, "ip"));
    }
}

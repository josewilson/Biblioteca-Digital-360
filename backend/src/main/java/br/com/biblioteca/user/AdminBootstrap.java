package br.com.biblioteca.user;

import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cria o primeiro SUPER_ADMIN a partir de ADMIN_EMAIL/ADMIN_PASSWORD, só se não houver nenhum usuário. */
@Component
class AdminBootstrap implements ApplicationRunner {

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    AdminBootstrap(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                   @Value("${app.admin.email:}") String email, @Value("${app.admin.password:}") String password) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank() || password.length() < 8 || users.count() > 0) {
            return;
        }
        users.save(new User("Administrador", email.trim().toLowerCase(), encoder.encode(password),
                roles.findByCodeIn(Set.of("SUPER_ADMIN"))));
    }
}

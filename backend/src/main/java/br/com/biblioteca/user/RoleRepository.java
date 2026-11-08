package br.com.biblioteca.user;

import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Set<Role> findByCodeIn(Collection<String> codes);
}

package br.com.biblioteca.user;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByCpf(String cpf);

    boolean existsByRegistration(String registration);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByCpfAndIdNot(String cpf, Long id);

    boolean existsByRegistrationAndIdNot(String registration, Long id);

    /** Versão atual da sessão, só de usuário ativo: consulta a cada requisição, por chave primária. */
    @Query("select u.tokenVersion from User u where u.id = :id and u.active = true and u.deletedAt is null")
    Optional<Integer> findActiveTokenVersion(@Param("id") Long id);

    /** Busca por nome (sem acento), e-mail ou matrícula. Cada coluna tem índice trigram (V9). */
    @Query("""
            select u from User u
            where function('f_unaccent' as String, lower(u.fullName)) like function('f_unaccent' as String, lower(:pattern)) escape '\\'
               or lower(u.email) like lower(:pattern) escape '\\'
               or lower(u.registration) like lower(:pattern) escape '\\'
            """)
    Page<User> search(@Param("pattern") String pattern, Pageable pageable);

    /** Trava o leitor: empréstimos simultâneos do mesmo leitor são serializados (limite não estoura). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findForUpdateById(@Param("id") Long id);
}

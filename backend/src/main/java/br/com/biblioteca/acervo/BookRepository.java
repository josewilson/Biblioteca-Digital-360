package br.com.biblioteca.acervo;

import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {

    boolean existsByIsbn(String isbn);

    @EntityGraph(attributePaths = "publisher")
    Page<Book> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "publisher")
    Page<Book> findAll(Pageable pageable);
}

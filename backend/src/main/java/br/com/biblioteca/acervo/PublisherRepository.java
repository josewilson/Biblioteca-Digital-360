package br.com.biblioteca.acervo;

import org.springframework.data.jpa.repository.JpaRepository;

interface PublisherRepository extends JpaRepository<Publisher, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}

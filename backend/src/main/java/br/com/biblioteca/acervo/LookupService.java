package br.com.biblioteca.acervo;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.biblioteca.shared.error.BusinessRuleException;
import br.com.biblioteca.shared.error.ResourceNotFoundException;

/** Cadastros simples de apoio: autores, editoras e categorias. */
@Service
public class LookupService {

    private static final Sort BY_NAME = Sort.by("name");

    private final AuthorRepository authors;
    private final PublisherRepository publishers;
    private final CategoryRepository categories;

    LookupService(AuthorRepository authors, PublisherRepository publishers, CategoryRepository categories) {
        this.authors = authors;
        this.publishers = publishers;
        this.categories = categories;
    }

    public NameResponse createAuthor(String name) {
        Author a = authors.save(new Author(name.trim()));
        return new NameResponse(a.getId(), a.getName());
    }

    public NameResponse createPublisher(String name) {
        if (publishers.existsByNameIgnoreCase(name.trim())) {
            throw new BusinessRuleException("Já existe editora com este nome.");
        }
        Publisher p = publishers.save(new Publisher(name.trim()));
        return new NameResponse(p.getId(), p.getName());
    }

    public NameResponse createCategory(String name) {
        if (categories.existsByNameIgnoreCase(name.trim())) {
            throw new BusinessRuleException("Já existe categoria com este nome.");
        }
        Category c = categories.save(new Category(name.trim()));
        return new NameResponse(c.getId(), c.getName());
    }

    @Transactional
    public NameResponse renameAuthor(Long id, String name) {
        Author a = authors.findById(id).orElseThrow(() -> new ResourceNotFoundException("Autor inexistente."));
        a.rename(name.trim());
        return new NameResponse(a.getId(), a.getName());
    }

    @Transactional
    public NameResponse renamePublisher(Long id, String name) {
        Publisher p = publishers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Editora inexistente."));
        if (publishers.existsByNameIgnoreCaseAndIdNot(name.trim(), id)) {
            throw new BusinessRuleException("Já existe editora com este nome.");
        }
        p.rename(name.trim());
        return new NameResponse(p.getId(), p.getName());
    }

    @Transactional
    public NameResponse renameCategory(Long id, String name) {
        Category c = categories.findById(id).orElseThrow(() -> new ResourceNotFoundException("Categoria inexistente."));
        if (categories.existsByNameIgnoreCaseAndIdNot(name.trim(), id)) {
            throw new BusinessRuleException("Já existe categoria com este nome.");
        }
        c.rename(name.trim());
        return new NameResponse(c.getId(), c.getName());
    }

    // ponytail: listas sem paginação (cadastros de apoio, poucos milhares); paginar se crescer.
    public List<NameResponse> listAuthors() {
        return authors.findAll(BY_NAME).stream().map(a -> new NameResponse(a.getId(), a.getName())).toList();
    }

    public List<NameResponse> listPublishers() {
        return publishers.findAll(BY_NAME).stream().map(p -> new NameResponse(p.getId(), p.getName())).toList();
    }

    public List<NameResponse> listCategories() {
        return categories.findAll(BY_NAME).stream().map(c -> new NameResponse(c.getId(), c.getName())).toList();
    }
}

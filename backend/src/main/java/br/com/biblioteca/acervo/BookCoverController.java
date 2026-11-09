package br.com.biblioteca.acervo;

import java.io.IOException;
import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.biblioteca.security.Access;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Capa da obra. A leitura é pública de propósito: a tag {@code <img>} não envia o token, e a capa de um livro não é
 * dado sensível. O envio e a remoção são da equipe.
 */
@RestController
@RequestMapping("/api/v1/books/{id}/cover")
public class BookCoverController {

    private final BookCoverService service;

    public BookCoverController(BookCoverService service) {
        this.service = service;
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Access.STAFF)
    public BookResponse upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                               Authentication auth, HttpServletRequest http) throws IOException {
        return service.store(id, file.getBytes(), Long.valueOf(auth.getName()), http.getRemoteAddr());
    }

    @DeleteMapping
    @PreAuthorize(Access.STAFF)
    public BookResponse remove(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return service.remove(id, Long.valueOf(auth.getName()), http.getRemoteAddr());
    }

    /** O endereço leva {@code ?v=<versão>}: trocou a capa, mudou a versão, então o cache longo é seguro. */
    @GetMapping
    public ResponseEntity<byte[]> image(@PathVariable Long id, @RequestParam(defaultValue = "full") String size) {
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(service.image(id, "thumb".equals(size)));
    }
}

package br.com.biblioteca.user;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

/** A própria pessoa, qualquer perfil, vê o que o sistema guarda sobre ela (LGPD, direito de acesso). */
@RestController
@RequestMapping("/api/v1/me")
public class MeController {

    private final UserService service;

    MeController(UserService service) {
        this.service = service;
    }

    @GetMapping("/data")
    public ResponseEntity<Map<String, Object>> myData(Authentication auth, HttpServletRequest http) {
        Long id = Long.valueOf(auth.getName());
        return UserController.download(id, service.export(id, id, http.getRemoteAddr()));
    }
}

package br.com.biblioteca.security;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return service.login(request, http.getRemoteAddr());
    }

    /** Devolve um token novo: os anteriores, inclusive o usado aqui, deixam de valer. */
    @PostMapping("/password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                        Authentication auth, HttpServletRequest http) {
        return service.changePassword(Long.valueOf(auth.getName()), request, http.getRemoteAddr());
    }
}

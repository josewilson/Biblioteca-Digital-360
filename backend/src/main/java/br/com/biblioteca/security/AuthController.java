package br.com.biblioteca.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;
    private final PasswordResetService resets;

    public AuthController(AuthService service, PasswordResetService resets) {
        this.service = service;
        this.resets = resets;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest http) {
        return service.login(request, http.getRemoteAddr());
    }

    /** Sempre 204, exista o e-mail ou não: a resposta não revela quem tem conta. */
    @PostMapping("/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgot(@Valid @RequestBody ForgotPasswordRequest request, HttpServletRequest http) {
        resets.request(request.email(), http.getRemoteAddr());
    }

    @PostMapping("/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody NewPasswordRequest request, HttpServletRequest http) {
        resets.reset(request.token(), request.newPassword(), http.getRemoteAddr());
    }

    /** Devolve um token novo: os anteriores, inclusive o usado aqui, deixam de valer. */
    @PostMapping("/password")
    public TokenResponse changePassword(@Valid @RequestBody ChangePasswordRequest request,
                                        Authentication auth, HttpServletRequest http) {
        return service.changePassword(Long.valueOf(auth.getName()), request, http.getRemoteAddr());
    }
}

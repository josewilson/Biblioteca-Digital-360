package br.com.biblioteca.user;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.biblioteca.security.Access;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize(Access.STAFF)
public class UserController {

    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request,
                                               Authentication auth, HttpServletRequest http) {
        UserResponse created = service.create(request, rolesOf(auth), callerId(auth), http.getRemoteAddr());
        return ResponseEntity.created(URI.create("/api/v1/users/" + created.id())).body(created);
    }

    @GetMapping
    public Page<UserResponse> list(@RequestParam(required = false) String q, Pageable pageable) {
        return service.list(q, pageable);
    }

    @GetMapping("/lookup")
    public List<UserResponse> lookup(@RequestParam List<Long> ids) {
        return service.lookup(ids);
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request,
                               Authentication auth, HttpServletRequest http) {
        return service.update(id, request, rolesOf(auth), callerId(auth), http.getRemoteAddr());
    }

    @PostMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request,
                                              Authentication auth, HttpServletRequest http) {
        service.resetPassword(id, request.newPassword(), rolesOf(auth), callerId(auth), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Access.ADMIN)
    public ResponseEntity<Void> deactivate(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        service.deactivate(id, rolesOf(auth), callerId(auth), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/export")
    @PreAuthorize(Access.ADMIN)
    public ResponseEntity<Map<String, Object>> export(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return download(id, service.export(id, callerId(auth), http.getRemoteAddr()));
    }

    @PostMapping("/{id}/anonymize")
    @PreAuthorize(Access.ADMIN)
    public ResponseEntity<Void> anonymize(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        service.anonymize(id, rolesOf(auth), callerId(auth), http.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    static ResponseEntity<Map<String, Object>> download(Long id, Map<String, Object> data) {
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"meus-dados-" + id + ".json\"")
                .body(data);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize(Access.ADMIN)
    public UserResponse reactivate(@PathVariable Long id, Authentication auth, HttpServletRequest http) {
        return service.reactivate(id, rolesOf(auth), callerId(auth), http.getRemoteAddr());
    }

    private static Long callerId(Authentication auth) {
        return Long.valueOf(auth.getName());
    }

    private static Set<String> rolesOf(Authentication auth) {
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                .collect(Collectors.toSet());
    }
}

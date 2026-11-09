package br.com.biblioteca.loan;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.biblioteca.security.Access;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/policies")
@PreAuthorize(Access.ADMIN)
public class PolicyController {

    private final PolicyService service;

    public PolicyController(PolicyService service) {
        this.service = service;
    }

    @GetMapping
    public List<PolicyResponse> list() {
        return service.list();
    }

    @PutMapping("/{role}")
    public PolicyResponse update(@PathVariable String role, @Valid @RequestBody PolicyRequest request,
                                 Authentication auth, HttpServletRequest http) {
        return service.update(role, request, Long.valueOf(auth.getName()), http.getRemoteAddr());
    }
}

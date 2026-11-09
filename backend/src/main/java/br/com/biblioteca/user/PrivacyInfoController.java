package br.com.biblioteca.user;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Público (a página de privacidade abre antes do login): quem responde por seus dados na instituição. */
@RestController
@RequestMapping("/api/v1/privacy")
public class PrivacyInfoController {

    private final String contact;

    PrivacyInfoController(@Value("${app.privacy.contact:}") String contact) {
        this.contact = contact.trim();
    }

    @GetMapping
    public Map<String, String> info() {
        return Map.of("contact", contact);
    }
}

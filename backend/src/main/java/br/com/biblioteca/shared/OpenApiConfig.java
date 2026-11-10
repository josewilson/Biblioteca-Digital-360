package br.com.biblioteca.shared;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/** Cabeçalho da especificação OpenAPI e o esquema de autenticação (JWT no cabeçalho Authorization: Bearer). */
@Configuration
class OpenApiConfig {

    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info().title("Biblioteca Digital 360 — API").version("v1")
                        .description("Sistema Integrado de Gestão Bibliotecária. Entre em POST /api/v1/auth/login e envie "
                                + "o accessToken em Authorization: Bearer. Erros seguem o formato ProblemDetail (RFC 7807). "
                                + "Detalhes das regras de negócio em docs/BUSINESS_RULES.md."))
                .components(new Components().addSecuritySchemes("bearer",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"));
    }
}

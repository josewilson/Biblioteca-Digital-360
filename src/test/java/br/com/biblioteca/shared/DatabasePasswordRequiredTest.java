package br.com.biblioteca.shared;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Usa o mecanismo real do Spring (placeholders de propriedade) para provar que a falta da senha derruba o início. */
class DatabasePasswordRequiredTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(DatabasePasswordRequired.class);

    @Test
    void semDbPasswordADefinirAAplicacaoRecusaIniciarCitandoAVariavel() {
        // É exatamente o que o application.yml produz quando DB_PASSWORD não existe no ambiente.
        runner.withPropertyValues("spring.datasource.password=${DB_PASSWORD_QUE_NAO_EXISTE}").run(context -> {
            assertTrue(context.getStartupFailure() != null, "o contexto deveria ter falhado ao iniciar");
            Throwable root = context.getStartupFailure();
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertTrue(root.getMessage().contains("DB_PASSWORD_QUE_NAO_EXISTE"), "a mensagem deve citar a variável que falta: " + root.getMessage());
        });
    }

    @Test
    void senhaVaziaTambemEhRecusada() {
        runner.withPropertyValues("spring.datasource.password=").run(context ->
                assertTrue(context.getStartupFailure() != null, "senha vazia não pode iniciar"));
    }

    @Test
    void comSenhaDefinidaIniciaNormalmente() {
        runner.withPropertyValues("spring.datasource.password=qualquer-senha").run(context ->
                assertTrue(context.getStartupFailure() == null, "com senha definida deve iniciar"));
    }
}

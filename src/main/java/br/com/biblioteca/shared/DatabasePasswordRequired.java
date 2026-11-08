package br.com.biblioteca.shared;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Falha cedo e com mensagem clara se a senha do banco não foi definida.
 *
 * Sem isto, o Spring deixa um {@code ${DB_PASSWORD}} não resolvido como TEXTO LITERAL ao ligar as propriedades do
 * datasource, e o erro só apareceria depois, como "falha de autenticação" ou "conexão recusada". Um
 * {@link BeanFactoryPostProcessor} roda ANTES de qualquer bean ser criado (inclusive o Flyway e o JPA, que já conectam
 * ao banco), e {@code Environment.getProperty} resolve placeholders de forma estrita: o erro cita a variável que falta.
 * A senha não tem valor padrão em lugar nenhum (decisão do projeto).
 */
@Configuration
class DatabasePasswordRequired {

    @Bean
    static BeanFactoryPostProcessor requireDatabasePassword(Environment environment) {
        return beanFactory -> {
            String password;
            try {
                password = environment.getProperty("spring.datasource.password");
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException(
                        "DB_PASSWORD não foi definida: defina a senha do banco na variável de ambiente DB_PASSWORD. (" + e.getMessage() + ")", e);
            }
            if (password == null || password.isBlank()) {
                throw new IllegalStateException("DB_PASSWORD está vazia: defina a senha do banco na variável de ambiente DB_PASSWORD.");
            }
        };
    }
}

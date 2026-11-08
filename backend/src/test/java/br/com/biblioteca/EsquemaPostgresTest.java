package br.com.biblioteca;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Aplica as migrações num PostgreSQL de verdade e prova que o banco recusa o que as regras proíbem.
 * Pulado automaticamente se o Docker não estiver rodando.
 */
@Testcontainers(disabledWithoutDocker = true)
class EsquemaPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrar() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .load()
                .migrate();
        sql("""
            INSERT INTO users (id, full_name, email) VALUES (1, 'Ana', 'ana@x.com');
            INSERT INTO books (id, title) VALUES (1, 'Dom Casmurro');
            INSERT INTO book_copies (id, book_id, barcode, kind) VALUES (1, 1, 'C-1', 'CONSULTA');
            INSERT INTO book_copies (id, book_id, barcode, kind) VALUES (2, 1, 'C-2', 'CIRCULANTE');
            """);
    }

    @Test
    void exemplarDeConsultaNaoPodeSerEmprestado() {
        assertThrows(SQLException.class, () -> sql(
                "INSERT INTO loans (user_id, copy_id, performed_by, due_at) VALUES (1, 1, 1, now() + interval '7 days')"));
    }

    @Test
    void exemplarNaoTemDoisEmprestimosAtivos() throws SQLException {
        String emprestar = "INSERT INTO loans (user_id, copy_id, performed_by, due_at) VALUES (1, 2, 1, now() + interval '7 days')";
        sql(emprestar);
        assertThrows(SQLException.class, () -> sql(emprestar));
    }

    private static void sql(String comando) throws SQLException {
        try (Connection c = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement s = c.createStatement()) {
            s.execute(comando);
        }
    }
}

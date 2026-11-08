package br.com.biblioteca;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BibliotecaTest {

    private final NotificadorDeTeste notificador = new NotificadorDeTeste();
    private final Biblioteca biblioteca = new Biblioteca(notificador);

    private Livro domCasmurro() {
        return new Livro("L-001", "Dom Casmurro");
    }

    private Leitor leitor(String nome, TipoDeLeitor tipo) {
        return new Leitor(nome, nome.toLowerCase() + "@email.com", tipo);
    }

    @Test
    void emprestarDeixaOLivroIndisponivel() {
        Livro livro = domCasmurro();
        biblioteca.emprestar(livro);
        assertFalse(livro.estaDisponivel());
    }

    @Test
    void naoEmprestaOMesmoLivroDuasVezes() {
        Livro livro = domCasmurro();
        biblioteca.emprestar(livro);
        assertThrows(IllegalStateException.class, () -> biblioteca.emprestar(livro));
    }

    @Test
    void devolverNoPrazoNaoGeraMulta() {
        Livro livro = domCasmurro();
        biblioteca.emprestar(livro);
        assertEquals(new BigDecimal("0.00"), biblioteca.devolver(livro, leitor("Ana", new Aluno()), 0));
        assertTrue(notificador.avisos.isEmpty());
    }

    @Test
    void devolverComAtrasoCobraAMulta() {
        Livro livro = domCasmurro();
        biblioteca.emprestar(livro);
        assertEquals(new BigDecimal("3.00"), biblioteca.devolver(livro, leitor("Ana", new Aluno()), 3));
        assertEquals(List.of("Ana: Multa de R$ 3,00 por 3 dia(s) de atraso."), notificador.avisos);
    }

    @Test
    void emprestarTodosEmprestaAListaDaTurma() {
        Livro iracema = new Livro("L-002", "Iracema");
        Livro cortico = new Livro("L-003", "O Cortiço");

        biblioteca.emprestarTodos(List.of(iracema, cortico));

        assertFalse(iracema.estaDisponivel());
        assertFalse(cortico.estaDisponivel());
    }
}

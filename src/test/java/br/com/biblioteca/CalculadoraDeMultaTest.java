package br.com.biblioteca;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CalculadoraDeMultaTest {

    private final CalculadoraDeMulta calculadora = new CalculadoraDeMulta();

    @Test
    void alunoPagaCemCentavosPorDia() {
        Leitor ana = new Leitor("Ana", "ana@email.com", new Aluno());
        assertEquals(new BigDecimal("3.00"), calculadora.calcular(ana, 3));
    }

    @Test
    void professorPagaCinquentaCentavosPorDia() {
        Leitor bruno = new Leitor("Bruno", "bruno@email.com", new Professor());
        assertEquals(new BigDecimal("1.50"), calculadora.calcular(bruno, 3));
    }

    @Test
    void visitantePagaDuzentosCentavosPorDia() {
        Leitor carla = new Leitor("Carla", "carla@email.com", new Visitante());
        assertEquals(new BigDecimal("6.00"), calculadora.calcular(carla, 3));
    }

    @Test
    void semAtrasoNaoHaMulta() {
        Leitor ana = new Leitor("Ana", "ana@email.com", new Aluno());
        assertEquals(new BigDecimal("0.00"), calculadora.calcular(ana, 0));
    }
}

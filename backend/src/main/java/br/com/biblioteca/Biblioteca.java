package br.com.biblioteca;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public class Biblioteca {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private final CalculadoraDeMulta calculadora = new CalculadoraDeMulta();
    private final Notificador notificador;

    public Biblioteca(Notificador notificador) {
        this.notificador = notificador;
    }

    public void emprestar(Livro livro) {
        livro.emprestar();
    }

    public BigDecimal devolver(Livro livro, Leitor leitor, int diasDeAtraso) {
        livro.devolver();

        BigDecimal multa = calculadora.calcular(leitor, diasDeAtraso);

        if (multa.signum() > 0) {
            notificador.avisar(leitor, String.format(PT_BR, "Multa de R$ %.2f por %d dia(s) de atraso.", multa, diasDeAtraso));
        }
        return multa;
    }

    public void emprestarTodos(List<Livro> livros) {
        for (Livro livro : livros) {
            livro.emprestar();
        }
    }
}

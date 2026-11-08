package br.com.biblioteca;

import java.math.BigDecimal;

/**
 * Um dia no balcão: Ana pega Dom Casmurro e devolve com 3 dias de atraso.
 */
public class App {

    public static void main(String[] args) {
        Biblioteca biblioteca = new Biblioteca(new EmailSmtp());

        Livro livro = new Livro("L-001", "Dom Casmurro");
        Leitor ana = new Leitor("Ana", "ana@email.com", new Aluno());

        biblioteca.emprestar(livro);
        System.out.println("Ana pegou " + livro.getTitulo());

        BigDecimal multa = biblioteca.devolver(livro, ana, 3);
        System.out.println("Ana devolveu com 3 dias de atraso. Multa: R$ " + multa);

        Leitor carla = new Leitor("Carla", "carla@email.com", new Visitante());
        CalculadoraDeMulta calculadora = new CalculadoraDeMulta();
        System.out.println("Carla, visitante, 3 dias de atraso: " + calculadora.calcular(carla, 3));
    }
}

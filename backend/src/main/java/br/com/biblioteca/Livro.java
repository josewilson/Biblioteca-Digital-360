package br.com.biblioteca;

/**
 * Um livro do acervo.
 * Os campos são privados: só o próprio livro muda o seu estado, e sempre pela regra.
 */
public class Livro implements ItemDoAcervo{

    private final String codigo;
    private final String titulo;
    private boolean disponivel = true;

    public Livro(String codigo, String titulo) {
        this.codigo = codigo;
        this.titulo = titulo;
    }

    public void emprestar() {
        if (!disponivel) {
            throw new IllegalStateException("O livro " + titulo + " já está emprestado.");
        }
        disponivel = false;
    }

    public void devolver() {
        if (disponivel) {
            throw new IllegalStateException("O livro " + titulo + " não está emprestado.");
        }
        disponivel = true;
    }
    public boolean estaDisponivel() {
        return disponivel;
    }
    public String getCodigo() {
        return codigo;
    }
    public String getTitulo() {
        return titulo;
    }
}

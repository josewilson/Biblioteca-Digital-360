package br.com.biblioteca;

/**
 * Quem pega livros emprestados.
 * O tipo (Aluno, Professor...) define quanto de multa o leitor paga por dia.
 */
public class Leitor {

    private final String nome;
    private final String email;
    private final TipoDeLeitor tipo;

    public Leitor(String nome, String email, TipoDeLeitor tipo) {
        this.nome = nome;
        this.email = email;
        this.tipo = tipo;
    }
    public String getNome() {
        return nome;
    }
    public String getEmail() {
        return email;
    }
    public TipoDeLeitor getTipo() {
        return tipo;
    }
}

package br.com.biblioteca;

public class ObraDeConsulta  implements ItemDoAcervo{

    private final String codigo;
    private final String titulo;

    public ObraDeConsulta(String codigo, String titulo) {
        this.codigo = codigo;
        this.titulo = titulo;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getTitulo() {
        return titulo;
    }
}

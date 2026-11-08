package br.com.biblioteca;

public class EbookLivre implements ItemDoAcervo, Baixavel {

    private final String codigo;
    private final String titulo;
    private final String linkDoPdf;

    public EbookLivre(String codigo, String titulo, String linkDoPdf) {
        this.codigo = codigo;
        this.titulo = titulo;
        this.linkDoPdf = linkDoPdf;
    }

    @Override
    public String getCodigo() {
        return codigo;
    }

    @Override
    public String getTitulo() {
        return titulo;
    }

    @Override
    public String baixarPdf() {
        return linkDoPdf;
    }
}
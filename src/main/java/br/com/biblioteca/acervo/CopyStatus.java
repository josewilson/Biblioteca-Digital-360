package br.com.biblioteca.acervo;

public enum CopyStatus {
    DISPONIVEL, EMPRESTADO, RESERVADO, PERDIDO, BAIXADO;

    /** Exemplar perdido ou baixado está fora de circulação e não conta como ativo. */
    public boolean isActive() {
        return this != PERDIDO && this != BAIXADO;
    }
}

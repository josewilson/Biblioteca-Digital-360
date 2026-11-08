package br.com.biblioteca;

import java.math.BigDecimal;

public class CalculadoraDeMulta {

    public BigDecimal calcular(Leitor leitor, int diasDeAtraso) {
        return leitor.getTipo().multaPorDia().multiply(BigDecimal.valueOf(diasDeAtraso));
    }
}
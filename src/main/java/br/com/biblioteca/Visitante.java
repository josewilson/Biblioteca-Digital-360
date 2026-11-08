package br.com.biblioteca;

import java.math.BigDecimal;

public class Visitante implements TipoDeLeitor {

    @Override
    public BigDecimal multaPorDia() {
        return new BigDecimal("2.00");
    }
}
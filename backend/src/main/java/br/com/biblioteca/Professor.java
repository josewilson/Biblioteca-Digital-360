package br.com.biblioteca;

import java.math.BigDecimal;

public class Professor implements TipoDeLeitor {

    @Override
    public BigDecimal multaPorDia() {
        return new BigDecimal("0.50");
    }
}
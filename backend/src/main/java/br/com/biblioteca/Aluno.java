package br.com.biblioteca;

import java.math.BigDecimal;

public class Aluno implements TipoDeLeitor {

    @Override
    public BigDecimal multaPorDia() {
        return new BigDecimal("1.00");
    }
}
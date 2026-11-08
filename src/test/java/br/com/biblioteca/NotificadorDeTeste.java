package br.com.biblioteca;

import java.util.ArrayList;
import java.util.List;

/**
 * Dublê de teste: não manda nada, só anota.
 * Assim o teste confere o aviso na hora, sem esperar 2 segundos.
 */
class NotificadorDeTeste implements Notificador {

    final List<String> avisos = new ArrayList<>();

    @Override
    public void avisar(Leitor leitor, String mensagem) {
        avisos.add(leitor.getNome() + ": " + mensagem);
    }
}

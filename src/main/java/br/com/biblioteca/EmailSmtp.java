package br.com.biblioteca;

public class EmailSmtp implements Notificador {

    @Override
    public void avisar(Leitor leitor, String mensagem) {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        System.out.println("[e-mail] para " + leitor.getEmail() + ": " + mensagem);
    }
}

package br.com.biblioteca.acervo;

import br.com.biblioteca.shared.error.BadRequestException;

/** ISBN: normaliza (sem hífen nem espaço), confere o dígito verificador e converte ISBN-10 em ISBN-13. */
public final class Isbn {

    private Isbn() {
    }

    /** @return o ISBN-13 (só dígitos) @throws BadRequestException se não for 10 ou 13 dígitos com verificador correto */
    public static String normalize(String raw) {
        String s = raw == null ? "" : raw.replaceAll("[\\s-]", "").toUpperCase();
        if (s.matches("\\d{13}") && sum13(s) % 10 == 0) {
            return s;
        }
        if (s.matches("\\d{9}[\\dX]") && sum10(s) % 11 == 0) {
            String core = "978" + s.substring(0, 9);
            return core + (10 - sum13(core + "0") % 10) % 10;
        }
        throw new BadRequestException("ISBN inválido: informe 10 ou 13 dígitos com o dígito verificador correto.");
    }

    /** @return o ISBN-10 equivalente, ou null se o ISBN-13 não começa com 978 */
    public static String toIsbn10(String isbn13) {
        if (!isbn13.startsWith("978")) {
            return null;
        }
        String core = isbn13.substring(3, 12);
        int check = (11 - sum10(core + "0") % 11) % 11;
        return core + (check == 10 ? "X" : String.valueOf(check));
    }

    private static int sum13(String s) {
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            sum += (s.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return sum;
    }

    private static int sum10(String s) {
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = s.charAt(i);
            sum += (c == 'X' ? 10 : c - '0') * (10 - i);
        }
        return sum;
    }
}

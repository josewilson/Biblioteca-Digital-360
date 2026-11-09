package br.com.biblioteca.shared;

/** Monta o padrão de LIKE a partir do texto digitado, sem deixar % e _ virarem curingas. */
public final class SearchText {

    private SearchText() {
    }

    public static String contains(String text) {
        return "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}

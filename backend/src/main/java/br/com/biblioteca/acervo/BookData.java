package br.com.biblioteca.acervo;

import java.util.List;

import tools.jackson.databind.JsonNode;

/** O que um provedor externo sabe sobre um ISBN. Campo que o provedor não traz fica nulo (ou lista vazia). */
record BookData(String title, String subtitle, List<String> authors, String publisher, Integer year, String language,
                String edition, String description, String coverUrl, List<String> subjects) {

    /** Texto do campo, ou nulo se ausente, nulo ou em branco. */
    static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return v.isString() && !v.asString().isBlank() ? v.asString().trim() : null;
    }

    /** Textos de um campo que é lista de strings (ignora o que não for texto). */
    static List<String> texts(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (!v.isArray()) {
            return List.of();
        }
        return v.valueStream().filter(JsonNode::isString).map(x -> x.asString().trim()).filter(x -> !x.isBlank()).toList();
    }
}

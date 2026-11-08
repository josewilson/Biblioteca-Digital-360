package br.com.biblioteca.acervo;

import jakarta.validation.constraints.NotNull;

public record StatusRequest(@NotNull CopyStatus status) {
}

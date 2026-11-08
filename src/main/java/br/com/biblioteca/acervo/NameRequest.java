package br.com.biblioteca.acervo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NameRequest(@NotBlank @Size(max = 150) String name) {
}

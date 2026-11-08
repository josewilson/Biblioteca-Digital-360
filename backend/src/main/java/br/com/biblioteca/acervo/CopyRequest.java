package br.com.biblioteca.acervo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CopyRequest(
        @NotBlank @Size(max = 40) String barcode,
        @NotNull CopyKind kind,
        CopyCondition condition,            // padrão: BOM
        @Size(max = 100) String location) {
}

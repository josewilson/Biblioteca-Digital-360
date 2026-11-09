package br.com.biblioteca.acervo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O tipo (consulta/emprestável) não muda aqui: há empréstimos e a regra da obra presos a ele. */
public record UpdateCopyRequest(
        @NotBlank @Size(max = 40) String barcode,
        CopyCondition condition,
        @Size(max = 100) String location) {
}

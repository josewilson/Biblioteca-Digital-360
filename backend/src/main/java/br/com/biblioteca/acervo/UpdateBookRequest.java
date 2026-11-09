package br.com.biblioteca.acervo;

import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Edita os dados da obra. Exemplares têm operações próprias, que protegem a regra de consulta/empréstimo. */
public record UpdateBookRequest(
        @NotBlank @Size(max = 250) String title,
        @Size(max = 20) String isbn,
        @Size(max = 40) String edition,
        @Min(1000) @Max(2200) Integer publicationYear,
        @Size(max = 30) String language,
        String description,
        @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "Use um endereço http:// ou https://") String coverUrl,
        Long publisherId,
        Set<Long> authorIds,
        Set<Long> categoryIds) implements BookDetails {
}

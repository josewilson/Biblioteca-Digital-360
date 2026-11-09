package br.com.biblioteca.acervo;

import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A obra nasce já com seus exemplares: precisa de pelo menos 1 CONSULTA e 1 CIRCULANTE. */
public record CreateBookRequest(
        @NotBlank @Size(max = 250) String title,
        @Size(max = 20) String isbn,
        @Size(max = 40) String edition,
        @Min(1000) @Max(2200) Integer publicationYear,
        @Size(max = 30) String language,
        String description,
        // Só http(s): o endereço vira <img src> na tela; esquemas como javascript: ou data: não são aceitos.
        @Size(max = 500) @Pattern(regexp = "^(https?://\\S+)?$", message = "Use um endereço http:// ou https://") String coverUrl,
        Long publisherId,
        Set<Long> authorIds,
        Set<Long> categoryIds,
        @NotEmpty @Valid List<CopyRequest> copies) implements BookDetails {
}

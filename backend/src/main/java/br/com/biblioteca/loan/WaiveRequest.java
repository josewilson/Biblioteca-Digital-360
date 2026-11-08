package br.com.biblioteca.loan;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WaiveRequest(@NotBlank @Size(max = 300) String reason) {
}

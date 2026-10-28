package utnfc.isi.back.tpi.animales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Motivo obligatorio para dejar un animal NO_DISPONIBLE. */
public record MotivoRequest(
        @Schema(example = "Tratamiento veterinario")
        @NotBlank(message = "es obligatorio") @Size(max = 500, message = "admite hasta 500 caracteres")
        String motivo) {
}

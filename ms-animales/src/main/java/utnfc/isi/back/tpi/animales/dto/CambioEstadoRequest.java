package utnfc.isi.back.tpi.animales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import utnfc.isi.back.tpi.animales.model.EstadoAnimal;

/** Cuerpo de la API interna: estado al que el proceso de adopcion quiere llevar al animal. */
public record CambioEstadoRequest(
        @Schema(example = "RESERVADO")
        @NotNull(message = "es obligatorio")
        EstadoAnimal nuevoEstado) {
}

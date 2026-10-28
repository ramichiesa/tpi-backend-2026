package utnfc.isi.back.tpi.adopciones.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import utnfc.isi.back.tpi.adopciones.model.ResultadoControl;

/** Cuerpos de las operaciones sobre solicitudes (CONTRATOS.md, seccion 7). */
public final class SolicitudRequests {

    private SolicitudRequests() {
    }

    public record Crear(
            @Schema(example = "1")
            @NotNull(message = "es obligatorio") @Positive(message = "debe ser positivo")
            Long animalId) {
    }

    /** La justificacion es obligatoria solo si la solicitud requiere excepcion por distancia (R-08). */
    public record Aprobar(
            @Schema(example = "El adoptante se compromete a trasladar al animal a los controles")
            @Size(max = 1000, message = "admite hasta 1000 caracteres")
            String justificacion) {
    }

    /** Motivo obligatorio: rechazo (R-08), cancelacion de entrega o devolucion (R-12). */
    public record Motivo(
            @Schema(example = "El hogar no reune las condiciones")
            @NotBlank(message = "es obligatorio") @Size(max = 1000, message = "admite hasta 1000 caracteres")
            String motivo) {
    }

    public record ControlFinal(
            @Schema(example = "CONFORME")
            @NotNull(message = "es obligatorio")
            ResultadoControl resultado,

            @Schema(example = "El animal se adapto bien")
            @Size(max = 1000, message = "admite hasta 1000 caracteres")
            String observaciones) {
    }
}

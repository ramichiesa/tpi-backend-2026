package utnfc.isi.back.tpi.adopciones.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import utnfc.isi.back.tpi.adopciones.model.TipoVivienda;

/** Perfil del adoptante. Todos los campos son obligatorios: sin ellos no se pueden evaluar R-04 a R-06 (R-03). */
public record PerfilRequest(
        @Schema(example = "Ana Perez")
        @NotBlank(message = "es obligatorio") @Size(max = 150, message = "admite hasta 150 caracteres")
        String nombreCompleto,

        @Schema(example = "3515550000")
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "[0-9 +()-]{6,40}", message = "debe ser un telefono valido")
        String telefono,

        @Schema(example = "Cordoba")
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "admite hasta 100 caracteres")
        String provincia,

        @Schema(example = "Cordoba")
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "admite hasta 100 caracteres")
        String localidad,

        @Schema(description = "Calle y altura, sin localidad", example = "Av Colon 1000")
        @NotBlank(message = "es obligatorio") @Size(max = 200, message = "admite hasta 200 caracteres")
        String direccion,

        @Schema(example = "CASA")
        @NotNull(message = "es obligatorio")
        TipoVivienda tipoVivienda,

        @NotNull(message = "es obligatorio")
        Boolean tienePatio,

        @NotNull(message = "es obligatorio")
        Boolean hayNinios,

        @NotNull(message = "es obligatorio")
        Boolean hayOtrosAnimales) {
}

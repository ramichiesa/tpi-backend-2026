package utnfc.isi.back.tpi.animales.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import utnfc.isi.back.tpi.animales.model.Especie;
import utnfc.isi.back.tpi.animales.model.Tamanio;

/** Datos de alta o modificacion de un animal (CONTRATOS.md, seccion 6.4). */
public record AnimalRequest(
        @Schema(example = "Luna")
        @NotBlank(message = "es obligatorio") @Size(max = 100, message = "admite hasta 100 caracteres")
        String nombre,

        @Schema(example = "PERRO")
        @NotNull(message = "es obligatorio")
        Especie especie,

        @Schema(example = "24")
        @NotNull(message = "es obligatorio") @PositiveOrZero(message = "debe ser mayor o igual a 0")
        @Max(value = 400, message = "no puede superar 400 meses")
        Integer edadMeses,

        @Schema(example = "MEDIANO")
        @NotNull(message = "es obligatorio")
        Tamanio tamanio,

        @Schema(example = "Ninguna")
        @Size(max = 500, message = "admite hasta 500 caracteres")
        String necesidadesEspeciales,

        @NotNull(message = "es obligatorio")
        Boolean aptoNinios,

        @NotNull(message = "es obligatorio")
        Boolean aptoOtrosAnimales,

        @NotNull(message = "es obligatorio")
        Boolean requiereEspacioExterior) {
}

package utnfc.isi.back.tpi.adopciones.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Lo que Adopciones necesita saber de un animal: su estado (R-01, R-07) y sus aptitudes
 * (R-04, R-05). Se ignoran los demas campos para tolerar que Animales agregue informacion.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AnimalDto(
        Long id,
        String nombre,
        EstadoAnimal estado,
        boolean aptoNinios,
        boolean aptoOtrosAnimales,
        boolean requiereEspacioExterior) {
}

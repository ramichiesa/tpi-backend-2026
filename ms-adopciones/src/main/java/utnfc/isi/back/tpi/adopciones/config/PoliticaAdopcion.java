package utnfc.isi.back.tpi.adopciones.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Parametros de politica configurables (documento de dominio, seccion 3.6).
 * Se cargan de las variables REFUGIO_LAT, REFUGIO_LON, DISTANCIA_MAX_KM, PLAZO_ENTREGA_DIAS
 * y PERIODO_ADAPTACION_DIAS. Si un valor es invalido el servicio no arranca.
 */
@Validated
@ConfigurationProperties("adopciones.politica")
public record PoliticaAdopcion(
        @Min(-90) @Max(90) double refugioLat,
        @Min(-180) @Max(180) double refugioLon,
        @Positive double distanciaMaxKm,
        @Positive int plazoEntregaDias,
        // 0 se admite solo para poder demostrar el control final sin esperar 30 dias
        @PositiveOrZero int periodoAdaptacionDias) {
}

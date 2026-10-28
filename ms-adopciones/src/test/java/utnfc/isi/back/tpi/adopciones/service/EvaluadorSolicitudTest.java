package utnfc.isi.back.tpi.adopciones.service;

import org.junit.jupiter.api.Test;
import utnfc.isi.back.tpi.adopciones.client.AnimalDto;
import utnfc.isi.back.tpi.adopciones.client.Coordenadas;
import utnfc.isi.back.tpi.adopciones.client.EstadoAnimal;
import utnfc.isi.back.tpi.adopciones.client.GeorefClient;
import utnfc.isi.back.tpi.adopciones.config.PoliticaAdopcion;
import utnfc.isi.back.tpi.adopciones.model.Perfil;
import utnfc.isi.back.tpi.adopciones.model.TipoVivienda;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** R-04, R-05 y R-06 con Georef reemplazado por un doble de prueba (sin Internet). */
class EvaluadorSolicitudTest {

    private static final PoliticaAdopcion POLITICA = new PoliticaAdopcion(-31.4334, -64.1918, 100, 7, 30);

    private final GeorefClient georef = mock(GeorefClient.class);
    private final EvaluadorSolicitud evaluador = new EvaluadorSolicitud(georef, POLITICA);

    private static Perfil perfil(boolean patio, boolean ninios, boolean otrosAnimales) {
        Perfil p = new Perfil("ana");
        p.actualizar("Ana", "3515550000", "Cordoba", "Cordoba", "Av Colon 1000", TipoVivienda.CASA,
                patio, ninios, otrosAnimales, LocalDateTime.now());
        return p;
    }

    private static AnimalDto animal(boolean aptoNinios, boolean aptoOtros, boolean exterior) {
        return new AnimalDto(1L, "Toby", EstadoAnimal.DISPONIBLE, aptoNinios, aptoOtros, exterior);
    }

    @Test
    void r04AnimalNoAptoConNiniosEnHogarConNiniosEsIncompatible() {
        assertThat(evaluador.incompatibilidadDura(animal(false, true, false), perfil(true, true, false)))
                .get().asString().contains("ninios");
    }

    @Test
    void r04AnimalNoAptoConOtrosAnimalesEnHogarConAnimalesEsIncompatible() {
        assertThat(evaluador.incompatibilidadDura(animal(true, false, false), perfil(true, false, true)))
                .get().asString().contains("otros animales");
    }

    @Test
    void r04SinConflictosNoHayIncompatibilidad() {
        assertThat(evaluador.incompatibilidadDura(animal(false, false, false), perfil(true, false, false))).isEmpty();
    }

    @Test
    void r05NecesitaExteriorYNoHayPatioGeneraObservacionSinBloquear() {
        assertThat(evaluador.observacionesDeCompatibilidad(animal(true, true, true), perfil(false, false, false)))
                .singleElement().asString().contains("R-05");
        assertThat(evaluador.observacionesDeCompatibilidad(animal(true, true, true), perfil(true, false, false)))
                .isEmpty();
    }

    @Test
    void r06DentroDelLimiteNoMarcaNada() {
        // Av. Colon 1000, Cordoba (coordenadas reales devueltas por Georef)
        when(georef.geolocalizar(any(), any(), any())).thenReturn(Optional.of(new Coordenadas(-31.4096, -64.1966)));

        EvaluadorSolicitud.ResultadoDistancia r = evaluador.evaluarDistancia(perfil(true, false, false));

        assertThat(r.distanciaKm()).isLessThan(5.0);
        assertThat(r.requiereExcepcion()).isFalse();
        assertThat(r.noVerificada()).isFalse();
        assertThat(r.observacion()).isNull();
    }

    @Test
    void r06FueraDelLimiteRequiereExcepcion() {
        // Ciudad de Buenos Aires: unos 650 km del refugio
        when(georef.geolocalizar(any(), any(), any())).thenReturn(Optional.of(new Coordenadas(-34.6037, -58.3816)));

        EvaluadorSolicitud.ResultadoDistancia r = evaluador.evaluarDistancia(perfil(true, false, false));

        assertThat(r.requiereExcepcion()).isTrue();
        assertThat(r.distanciaKm()).isGreaterThan(600.0);
        assertThat(r.observacion()).contains("R-06");
    }

    @Test
    void r06SiGeorefNoEncuentraLaDireccionQuedaNoVerificada() {
        when(georef.geolocalizar(any(), any(), any())).thenReturn(Optional.empty());

        EvaluadorSolicitud.ResultadoDistancia r = evaluador.evaluarDistancia(perfil(true, false, false));

        assertThat(r.noVerificada()).isTrue();
        assertThat(r.distanciaKm()).isNull();
        assertThat(r.requiereExcepcion()).isFalse();
    }

    @Test
    void r06SiGeorefNoRespondeQuedaNoVerificadaYNoSePropagaElError() {
        when(georef.geolocalizar(any(), any(), any())).thenThrow(new ServicioNoDisponibleException("timeout"));

        EvaluadorSolicitud.ResultadoDistancia r = evaluador.evaluarDistancia(perfil(true, false, false));

        assertThat(r.noVerificada()).isTrue();
        assertThat(r.observacion()).contains("no respondio");
    }
}

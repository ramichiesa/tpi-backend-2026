package utnfc.isi.back.tpi.adopciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import utnfc.isi.back.tpi.adopciones.client.AnimalDto;
import utnfc.isi.back.tpi.adopciones.client.Coordenadas;
import utnfc.isi.back.tpi.adopciones.client.GeorefClient;
import utnfc.isi.back.tpi.adopciones.config.PoliticaAdopcion;
import utnfc.isi.back.tpi.adopciones.model.Perfil;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Evaluacion automatica de compatibilidad y proximidad entre un animal y un hogar.
 * El sistema recomienda y bloquea, pero la decision final de aprobar es del Coordinador.
 *
 * - R-04: incompatibilidad dura (bloquea).
 * - R-05: compatibilidad con observaciones (no bloquea).
 * - R-06: distancia maxima con coordenadas de Georef (marca "requiere excepcion" o
 *         "distancia no verificada"; nunca bloquea, porque una falla externa no debe impedir
 *         la operacion y la decision final ya es humana).
 */
@Component
public class EvaluadorSolicitud {

    private static final Logger log = LoggerFactory.getLogger(EvaluadorSolicitud.class);

    private final GeorefClient georefClient;
    private final PoliticaAdopcion politica;

    public EvaluadorSolicitud(GeorefClient georefClient, PoliticaAdopcion politica) {
        this.georefClient = georefClient;
        this.politica = politica;
    }

    /** R-04. Devuelve el motivo del rechazo si hay incompatibilidad dura. */
    public Optional<String> incompatibilidadDura(AnimalDto animal, Perfil perfil) {
        List<String> motivos = new ArrayList<>();
        if (!animal.aptoNinios() && perfil.isHayNinios()) {
            motivos.add("el animal no es apto para convivir con ninios y en el hogar hay ninios");
        }
        if (!animal.aptoOtrosAnimales() && perfil.isHayOtrosAnimales()) {
            motivos.add("el animal no es apto para convivir con otros animales y en el hogar hay otros animales");
        }
        return motivos.isEmpty()
                ? Optional.empty()
                : Optional.of("Incompatibilidad (R-04): " + String.join("; ", motivos));
    }

    /** R-05. Observaciones que no bloquean pero quedan visibles para el Coordinador. */
    public List<String> observacionesDeCompatibilidad(AnimalDto animal, Perfil perfil) {
        List<String> observaciones = new ArrayList<>();
        if (animal.requiereEspacioExterior() && !perfil.isTienePatio()) {
            observaciones.add("El animal necesita espacio exterior y el hogar no tiene patio (R-05)");
        }
        return observaciones;
    }

    /** R-06. Consulta Georef y compara la distancia con el maximo configurado. */
    public ResultadoDistancia evaluarDistancia(Perfil perfil) {
        Optional<Coordenadas> ubicacion;
        try {
            ubicacion = georefClient.geolocalizar(perfil.getDireccion(), perfil.getLocalidad(), perfil.getProvincia());
        } catch (ServicioNoDisponibleException e) {
            log.warn("Distancia no verificada: Georef no respondio");
            return ResultadoDistancia.noVerificada("No se pudo verificar la distancia: Georef no respondio (R-06)");
        }
        if (ubicacion.isEmpty()) {
            return ResultadoDistancia.noVerificada("No se pudo verificar la distancia: Georef no encontro la direccion (R-06)");
        }
        double km = CalculadoraDistancia.km(ubicacion.get(), new Coordenadas(politica.refugioLat(), politica.refugioLon()));
        if (km > politica.distanciaMaxKm()) {
            return new ResultadoDistancia(km, true, false,
                    "La distancia al refugio (" + km + " km) supera el maximo de " + politica.distanciaMaxKm()
                            + " km: aprobar requiere una justificacion (R-06, R-08)");
        }
        return new ResultadoDistancia(km, false, false, null);
    }

    /** Resultado de R-06. {@code observacion} es null cuando no hay nada que advertir. */
    public record ResultadoDistancia(Double distanciaKm, boolean requiereExcepcion, boolean noVerificada,
                                     String observacion) {
        static ResultadoDistancia noVerificada(String observacion) {
            return new ResultadoDistancia(null, false, true, observacion);
        }
    }
}

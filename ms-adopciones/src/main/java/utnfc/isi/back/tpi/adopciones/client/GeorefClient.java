package utnfc.isi.back.tpi.adopciones.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de la API Georef (Datos Argentina): GET {base}/direcciones.
 *
 * Parametros usados: direccion (calle y altura, sin localidad), provincia, localidad y max=1.
 * De la respuesta solo se toma direcciones[0].ubicacion.lat/lon. Formato verificado contra la
 * API real el 27/10/2026:
 * {"cantidad":1,"direcciones":[{..., "ubicacion":{"lat":-31.40,"lon":-64.19}}], ...}
 *
 * - Direccion no encontrada (cantidad 0 o ubicacion sin coordenadas): Optional vacio.
 * - Error de red, timeout, 4xx/5xx o formato inesperado: ServicioNoDisponibleException.
 * Quien lo usa decide que hacer con cada caso (R-06: "distancia no verificada").
 */
public class GeorefClient {

    private static final Logger log = LoggerFactory.getLogger(GeorefClient.class);

    private final RestClient restClient;

    public GeorefClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public Optional<Coordenadas> geolocalizar(String direccion, String localidad, String provincia) {
        RespuestaDirecciones respuesta;
        try {
            respuesta = restClient.get()
                    .uri(uri -> uri.path("/direcciones")
                            .queryParam("direccion", direccion)
                            .queryParam("localidad", localidad)
                            .queryParam("provincia", provincia)
                            .queryParam("max", 1)
                            .build())
                    .retrieve()
                    .body(RespuestaDirecciones.class);
        } catch (RestClientException e) {
            // No se registra la direccion completa (CONTRATOS.md, seccion 5)
            log.warn("Georef no respondio correctamente: {}", e.getClass().getSimpleName());
            throw new ServicioNoDisponibleException("La API Georef no responde", e);
        }
        if (respuesta == null || respuesta.direcciones() == null) {
            throw new ServicioNoDisponibleException("La API Georef devolvio una respuesta inesperada");
        }
        return respuesta.direcciones().stream()
                .findFirst()
                .map(DireccionGeoref::ubicacion)
                .filter(u -> u.lat() != null && u.lon() != null)
                .map(u -> new Coordenadas(u.lat(), u.lon()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RespuestaDirecciones(Integer cantidad, List<DireccionGeoref> direcciones) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record DireccionGeoref(String nomenclatura, Ubicacion ubicacion) {
        @Override
        public Ubicacion ubicacion() {
            return ubicacion == null ? new Ubicacion(null, null) : ubicacion;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Ubicacion(Double lat, Double lon) {
    }
}

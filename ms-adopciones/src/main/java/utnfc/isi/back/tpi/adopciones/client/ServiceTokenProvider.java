package utnfc.isi.back.tpi.adopciones.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Obtiene de Keycloak el token del cliente confidencial "adopciones-service"
 * (OAuth2 client credentials) y lo reutiliza mientras no este por vencer.
 *
 * Se usa para llamar a la API interna de Animales tanto en operaciones iniciadas por un
 * usuario como en la tarea programada, donde no hay ningun usuario. El token del usuario
 * NO se reenvia a Animales: la API interna exige el rol SERVICIO, que ningun humano tiene.
 */
public class ServiceTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenProvider.class);
    /** Margen para renovar el token antes de que venza durante una llamada. */
    private static final long MARGEN_SEGUNDOS = 30;

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final Clock clock;

    private String token;
    private Instant vencimiento = Instant.EPOCH;

    public ServiceTokenProvider(RestClient restClient, String tokenUri, String clientId, String clientSecret, Clock clock) {
        this.restClient = restClient;
        this.tokenUri = tokenUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clock = clock;
    }

    public synchronized String obtenerToken() {
        if (token == null || clock.instant().isAfter(vencimiento.minusSeconds(MARGEN_SEGUNDOS))) {
            renovar();
        }
        return token;
    }

    /** Descarta el token cacheado (por ejemplo, si Animales lo rechazo con 401). */
    public synchronized void invalidar() {
        token = null;
    }

    private void renovar() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        try {
            Map<?, ?> respuesta = restClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            if (respuesta == null || !(respuesta.get("access_token") instanceof String accessToken)) {
                throw new ServicioNoDisponibleException("Keycloak devolvio una respuesta sin access_token");
            }
            long expiraEn = respuesta.get("expires_in") instanceof Number n ? n.longValue() : 60;
            this.token = accessToken;
            this.vencimiento = clock.instant().plusSeconds(expiraEn);
            log.debug("Token de servicio renovado; vence en {} s", expiraEn);
        } catch (RestClientException e) {
            // Nunca se registra el secreto ni el token
            log.warn("No se pudo obtener el token de servicio: {}", e.getClass().getSimpleName());
            throw new ServicioNoDisponibleException("No se pudo obtener el token de servicio de Keycloak", e);
        }
    }
}

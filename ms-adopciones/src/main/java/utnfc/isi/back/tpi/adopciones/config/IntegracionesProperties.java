package utnfc.isi.back.tpi.adopciones.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Direcciones, credenciales y tiempos maximos de espera de los sistemas con los que
 * colabora Adopciones: el servicio de Animales, Keycloak (token de servicio) y Georef.
 */
@ConfigurationProperties("adopciones.integraciones")
public record IntegracionesProperties(Animales animales, Keycloak keycloak, Georef georef) {

    public record Animales(String baseUrl, Duration timeout) {
    }

    /** Cliente confidencial "adopciones-service" con grant client_credentials (rol SERVICIO). */
    public record Keycloak(String tokenUri, String clientId, String clientSecret, Duration timeout) {
    }

    public record Georef(String baseUrl, Duration timeout) {
    }
}

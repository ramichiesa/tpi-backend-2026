package utnfc.isi.back.tpi.donaciones.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Politica de donaciones (D-01) y conexion con Mercado Pago.
 * El access token llega solo por variable de entorno (MP_ACCESS_TOKEN) y nunca se registra.
 */
@Validated
@ConfigurationProperties("donaciones")
public record DonacionesProperties(
        @NotNull @Positive BigDecimal montoMinimo,
        MercadoPago mercadoPago) {

    public record MercadoPago(String baseUrl, String accessToken, Duration timeout, boolean usarSandboxInitPoint,
                              String tituloItem) {
    }
}

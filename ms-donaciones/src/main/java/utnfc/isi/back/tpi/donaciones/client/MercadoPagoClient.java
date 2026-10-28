package utnfc.isi.back.tpi.donaciones.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Adaptador de la API de Mercado Pago (Checkout Pro), segun la documentacion oficial:
 *
 * - Crear el cobro: POST /checkout/preferences con items (title, quantity, unit_price, currency_id)
 *   y external_reference. La respuesta trae id e init_point (enlace donde paga el donante).
 * - Verificar: GET /v1/payments/search?external_reference=... ; results[].status == "approved"
 *   indica pago aprobado y acreditado.
 *
 * Autenticacion: Authorization: Bearer {MP_ACCESS_TOKEN} (credencial de la cuenta vendedora de prueba).
 * Cualquier error de red, timeout, 4xx/5xx o formato inesperado se traduce a
 * ServicioNoDisponibleException; quien lo usa decide que hacer (crear: 503; verificar: sigue PENDIENTE).
 */
public class MercadoPagoClient {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoClient.class);
    static final String APROBADO = "approved";

    private final RestClient restClient;
    private final boolean usarSandboxInitPoint;

    public MercadoPagoClient(RestClient restClient, boolean usarSandboxInitPoint) {
        this.restClient = restClient;
        this.usarSandboxInitPoint = usarSandboxInitPoint;
    }

    public Cobro crearCobro(String referencia, BigDecimal monto, String titulo) {
        Map<String, Object> cuerpo = Map.of(
                "items", List.of(Map.of(
                        "id", "donacion",
                        "title", titulo,
                        "quantity", 1,
                        "unit_price", monto,
                        "currency_id", "ARS")),
                "external_reference", referencia);
        Preferencia preferencia;
        try {
            preferencia = restClient.post()
                    .uri("/checkout/preferences")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(Preferencia.class);
        } catch (RestClientResponseException e) {
            registrarRechazo("crear el cobro", e);
            throw new ServicioNoDisponibleException("Mercado Pago no pudo crear el cobro", e);
        } catch (RestClientException e) {
            log.warn("Mercado Pago no responde al crear el cobro: {}", e.getClass().getSimpleName());
            throw new ServicioNoDisponibleException("Mercado Pago no responde", e);
        }
        String link = preferencia == null ? null
                : (usarSandboxInitPoint ? preferencia.sandboxInitPoint() : preferencia.initPoint());
        if (preferencia == null || preferencia.id() == null || link == null) {
            throw new ServicioNoDisponibleException("Mercado Pago devolvio una respuesta inesperada al crear el cobro");
        }
        return new Cobro(preferencia.id(), link);
    }

    /** Devuelve el pago aprobado asociado a la referencia, si existe. */
    public Optional<String> buscarPagoAprobado(String referencia) {
        BusquedaPagos busqueda;
        try {
            busqueda = restClient.get()
                    .uri(uri -> uri.path("/v1/payments/search")
                            .queryParam("external_reference", referencia)
                            .queryParam("sort", "date_created")
                            .queryParam("criteria", "desc")
                            .build())
                    .retrieve()
                    .body(BusquedaPagos.class);
        } catch (RestClientResponseException e) {
            registrarRechazo("consultar pagos", e);
            throw new ServicioNoDisponibleException("Mercado Pago no pudo informar el estado del pago", e);
        } catch (RestClientException e) {
            log.warn("Mercado Pago no responde al consultar pagos: {}", e.getClass().getSimpleName());
            throw new ServicioNoDisponibleException("Mercado Pago no responde", e);
        }
        if (busqueda == null || busqueda.results() == null) {
            throw new ServicioNoDisponibleException("Mercado Pago devolvio una respuesta inesperada al consultar pagos");
        }
        return busqueda.results().stream()
                .filter(p -> APROBADO.equals(p.status()) && p.id() != null)
                .map(p -> String.valueOf(p.id()))
                .findFirst();
    }

    private static void registrarRechazo(String operacion, RestClientResponseException e) {
        if (e.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()
                || e.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
            // Nunca se registra el token
            log.error("Mercado Pago rechazo las credenciales al {} ({}). Revisar MP_ACCESS_TOKEN",
                    operacion, e.getStatusCode());
        } else {
            log.warn("Mercado Pago respondio {} al {}", e.getStatusCode(), operacion);
        }
    }

    /** Resultado de crear el cobro: id de la preferencia y enlace de pago. */
    public record Cobro(String preferenciaId, String linkPago) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Preferencia(String id,
                       @JsonProperty("init_point") String initPoint,
                       @JsonProperty("sandbox_init_point") String sandboxInitPoint) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BusquedaPagos(List<Pago> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Pago(Long id, String status) {
    }
}

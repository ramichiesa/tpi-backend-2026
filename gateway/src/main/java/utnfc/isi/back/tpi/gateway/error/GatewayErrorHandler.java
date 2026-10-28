package utnfc.isi.back.tpi.gateway.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

/**
 * Traduce los errores del Gateway al formato unico:
 * - servicio interno caido o inaccesible -> 503 (CONTRATOS.md: "otro servicio no responde");
 * - el servicio tardo mas que el response-timeout -> 504;
 * - ruta inexistente y otros ResponseStatusException -> su codigo.
 * Se ejecuta antes que el manejador por defecto de Spring Boot (orden -1).
 */
@Component
@Order(-2)
public class GatewayErrorHandler implements WebExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayErrorHandler.class);

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        if (esCausadoPor(ex, ConnectException.class) || esCausadoPor(ex, UnknownHostException.class)) {
            log.warn("Servicio interno no disponible para {}: {}", exchange.getRequest().getPath(), ex.getMessage());
            return JsonErrorWriter.escribir(exchange, HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                    "El servicio que atiende esta ruta no esta disponible");
        }
        if (esCausadoPor(ex, TimeoutException.class)) {
            return JsonErrorWriter.escribir(exchange, HttpStatus.GATEWAY_TIMEOUT, "Tiempo de espera agotado",
                    "El servicio que atiende esta ruta no respondio a tiempo");
        }
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (status == HttpStatus.GATEWAY_TIMEOUT) {
                return JsonErrorWriter.escribir(exchange, status, "Tiempo de espera agotado",
                        "El servicio que atiende esta ruta no respondio a tiempo");
            }
            String mensaje = status == HttpStatus.NOT_FOUND
                    ? "No existe el recurso " + exchange.getRequest().getPath().value()
                    : status.getReasonPhrase();
            return JsonErrorWriter.escribir(exchange, status, status.getReasonPhrase(), mensaje);
        }
        log.error("Error inesperado en el Gateway para {}", exchange.getRequest().getPath(), ex);
        return JsonErrorWriter.escribir(exchange, HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Error interno del Gateway");
    }

    private static boolean esCausadoPor(Throwable ex, Class<? extends Throwable> tipo) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (tipo.isInstance(t)) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }
}

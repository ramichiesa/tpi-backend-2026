package utnfc.isi.back.tpi.gateway.error;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * Escribe errores con el mismo formato JSON que usan los servicios (CONTRATOS.md, seccion 4),
 * para que el consumidor reciba siempre la misma estructura aunque el error ocurra en el Gateway.
 */
public final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    public static Mono<Void> escribir(ServerWebExchange exchange, HttpStatus status, String error, String mensaje) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = "{\"timestamp\":\"" + LocalDateTime.now() + "\",\"status\":" + status.value()
                + ",\"error\":\"" + escapar(error) + "\",\"message\":\"" + escapar(mensaje)
                + "\",\"path\":\"" + escapar(exchange.getRequest().getPath().value()) + "\",\"errores\":[]}";
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(buffer));
    }

    private static String escapar(String texto) {
        return texto == null ? "" : texto.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

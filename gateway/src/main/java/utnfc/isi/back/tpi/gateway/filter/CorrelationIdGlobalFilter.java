package utnfc.isi.back.tpi.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Genera el X-Correlation-ID si no viene, lo agrega a la peticion que se reenvia al servicio y a
 * la respuesta. Con el mismo identificador se puede seguir una operacion en los logs del Gateway,
 * de Adopciones y de Animales. Es reactivo, por eso no reutiliza el filtro de servlet del JAR comun.
 */
@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String HEADER = "X-Correlation-ID";
    private static final Logger log = LoggerFactory.getLogger(CorrelationIdGlobalFilter.class);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String recibido = exchange.getRequest().getHeaders().getFirst(HEADER);
        String correlationId = (recibido == null || recibido.isBlank() || recibido.length() > 100)
                ? UUID.randomUUID().toString() : recibido;
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(h -> h.set(HEADER, correlationId))
                .build();
        exchange.getResponse().getHeaders().set(HEADER, correlationId);
        log.info("[{}] {} {}", correlationId, request.getMethod(), request.getPath());
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

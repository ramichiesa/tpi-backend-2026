package utnfc.isi.back.tpi.common.correlation;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Interceptor para RestClient: reenvia el X-Correlation-ID actual en las llamadas a otros
 * servicios propios, de modo que una operacion distribuida se pueda seguir en los logs.
 * No se agrega a las llamadas a APIs externas (Georef, Mercado Pago).
 */
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null && !request.getHeaders().containsKey(CorrelationIdFilter.HEADER)) {
            request.getHeaders().add(CorrelationIdFilter.HEADER, correlationId);
        }
        return execution.execute(request, body);
    }
}

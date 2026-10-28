package utnfc.isi.back.tpi.adopciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import utnfc.isi.back.tpi.adopciones.client.AnimalesClient;
import utnfc.isi.back.tpi.adopciones.client.GeorefClient;
import utnfc.isi.back.tpi.adopciones.client.ServiceTokenProvider;
import utnfc.isi.back.tpi.common.correlation.CorrelationIdInterceptor;

import java.time.Clock;
import java.time.Duration;

/**
 * Crea los clientes HTTP (RestClient, como indica la catedra para Spring MVC) con tiempos
 * maximos de espera: si otro sistema no responde, la operacion falla rapido y de forma
 * controlada en lugar de dejar hilos colgados.
 */
@Configuration
public class ClientesConfig {

    private static SimpleClientHttpRequestFactory conTimeout(Duration timeout) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return factory;
    }

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    ServiceTokenProvider serviceTokenProvider(IntegracionesProperties props, Clock clock) {
        IntegracionesProperties.Keycloak kc = props.keycloak();
        RestClient restClient = RestClient.builder().requestFactory(conTimeout(kc.timeout())).build();
        return new ServiceTokenProvider(restClient, kc.tokenUri(), kc.clientId(), kc.clientSecret(), clock);
    }

    @Bean
    AnimalesClient animalesClient(IntegracionesProperties props, ServiceTokenProvider tokenProvider) {
        RestClient restClient = RestClient.builder()
                .baseUrl(props.animales().baseUrl())
                .requestFactory(conTimeout(props.animales().timeout()))
                // reenvia el X-Correlation-ID para seguir la operacion entre servicios
                .requestInterceptor(new CorrelationIdInterceptor())
                .build();
        return new AnimalesClient(restClient, tokenProvider);
    }

    @Bean
    GeorefClient georefClient(IntegracionesProperties props) {
        RestClient restClient = RestClient.builder()
                .baseUrl(props.georef().baseUrl())
                .requestFactory(conTimeout(props.georef().timeout()))
                .build();
        return new GeorefClient(restClient);
    }
}

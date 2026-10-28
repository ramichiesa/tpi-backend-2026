package utnfc.isi.back.tpi.donaciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import utnfc.isi.back.tpi.donaciones.client.MercadoPagoClient;

import java.time.Clock;

@Configuration
public class ClientesConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }

    @Bean
    MercadoPagoClient mercadoPagoClient(DonacionesProperties props) {
        DonacionesProperties.MercadoPago mp = props.mercadoPago();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(mp.timeout());
        factory.setReadTimeout(mp.timeout());
        RestClient restClient = RestClient.builder()
                .baseUrl(mp.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + mp.accessToken())
                .build();
        return new MercadoPagoClient(restClient, mp.usarSandboxInitPoint());
    }
}

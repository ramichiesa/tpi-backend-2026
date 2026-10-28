package utnfc.isi.back.tpi.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;

/**
 * Security Filter y enrutamiento del Gateway. Los servicios apuntan a un puerto cerrado para
 * comprobar, ademas, que una caida interna se informa como 503 con el formato unico.
 */
@SpringBootTest
@AutoConfigureWebTestClient
@TestPropertySource(properties = {
        "ANIMALES_URL=http://127.0.0.1:1",
        "ADOPCIONES_URL=http://127.0.0.1:1",
        "DONACIONES_URL=http://127.0.0.1:1"
})
class GatewaySecurityTest {

    @Autowired
    private WebTestClient client;

    private WebTestClient con(String rol) {
        return client.mutateWith(mockJwt().jwt(j -> j.subject("u-" + rol))
                .authorities(new SimpleGrantedAuthority("ROLE_" + rol)));
    }

    @Test
    void healthEsPublico() {
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }

    @Test
    void sinTokenDevuelve401ConFormatoUnico() {
        client.get().uri("/api/animales").exchange()
                .expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.status").isEqualTo(401).jsonPath("$.path").isEqualTo("/api/animales");
    }

    @Test
    void tokenDeServicioNoPuedeUsarLaApiPublica() {
        con("SERVICIO").get().uri("/api/animales").exchange().expectStatus().isForbidden();
    }

    @Test
    void laApiInternaNoSePublica() {
        con("COORDINADOR").put().uri("/internal/animales/1/estado").exchange().expectStatus().isForbidden();
    }

    @Test
    void conTokenValidoEnrutaYUnServicioCaidoSeInformaComo503() {
        con("ADOPTANTE").get().uri("/api/solicitudes").header("X-Correlation-ID", "demo-123").exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().valueEquals("X-Correlation-ID", "demo-123")
                .expectBody().jsonPath("$.error").isEqualTo("Servicio no disponible");
    }

    @Test
    void generaElIdentificadorDeCorrelacionSiNoViene() {
        con("COORDINADOR").get().uri("/api/donaciones").exchange()
                .expectHeader().exists("X-Correlation-ID");
    }

    @Test
    void rutaInexistenteDevuelve404() {
        con("ADOPTANTE").get().uri("/api/inexistente").exchange()
                .expectStatus().isNotFound()
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }
}

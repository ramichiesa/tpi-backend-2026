package utnfc.isi.back.tpi.adopciones.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Client credentials contra Keycloak: pedido correcto, cache del token y falla controlada. */
class ServiceTokenProviderTest {

    private static final String TOKEN_URI = "http://keycloak:8080/realms/adopciones/protocol/openid-connect/token";

    private MockRestServiceServer server;
    private ServiceTokenProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock clock = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);
        provider = new ServiceTokenProvider(builder.build(), TOKEN_URI, "adopciones-service", "secreto", clock);
    }

    @Test
    void pideElTokenUnaVezYLoReutilizaMientrasEsteVigente() {
        server.expect(ExpectedCount.once(), requestTo(TOKEN_URI))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(java.util.Map.of(
                        "grant_type", "client_credentials", "client_id", "adopciones-service")))
                .andRespond(withSuccess("{\"access_token\":\"abc\",\"expires_in\":300}", MediaType.APPLICATION_JSON));

        assertThat(provider.obtenerToken()).isEqualTo("abc");
        assertThat(provider.obtenerToken()).isEqualTo("abc");
        server.verify();
    }

    @Test
    void trasInvalidarloPideUnoNuevo() {
        server.expect(ExpectedCount.twice(), requestTo(TOKEN_URI))
                .andRespond(withSuccess("{\"access_token\":\"abc\",\"expires_in\":300}", MediaType.APPLICATION_JSON));

        provider.obtenerToken();
        provider.invalidar();
        provider.obtenerToken();
        server.verify();
    }

    @Test
    void keycloakCaidoSeTraduceAServicioNoDisponible() {
        server.expect(requestTo(TOKEN_URI)).andRespond(withServerError());

        assertThatThrownBy(provider::obtenerToken).isInstanceOf(ServicioNoDisponibleException.class);
    }
}

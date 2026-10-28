package utnfc.isi.back.tpi.donaciones.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.io.IOException;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Contrato con Mercado Pago segun su documentacion oficial, sin llamadas reales
 * (las pruebas automatizadas no dependen de Internet ni de credenciales).
 */
class MercadoPagoClientTest {

    private static final String BASE = "https://api.mercadopago.com";

    private MockRestServiceServer server;
    private MercadoPagoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MercadoPagoClient(builder.build(), false);
    }

    @Test
    void creaLaPreferenciaConReferenciaExternaYMontoEnPesos() {
        server.expect(requestTo(BASE + "/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.external_reference").value("ref-1"))
                .andExpect(jsonPath("$.items[0].unit_price").value(500))
                .andExpect(jsonPath("$.items[0].currency_id").value("ARS"))
                .andExpect(jsonPath("$.items[0].quantity").value(1))
                .andRespond(withSuccess("""
                        {"id":"123-abc","init_point":"https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=123-abc",
                         "sandbox_init_point":"https://sandbox.mercadopago.com.ar/checkout/v1/redirect?pref_id=123-abc"}
                        """, MediaType.APPLICATION_JSON));

        MercadoPagoClient.Cobro cobro = client.crearCobro("ref-1", new BigDecimal("500"), "Donacion al refugio");

        assertThat(cobro.preferenciaId()).isEqualTo("123-abc");
        assertThat(cobro.linkPago()).startsWith("https://www.mercadopago.com.ar/");
        server.verify();
    }

    @Test
    void credencialesInvalidasAlCrearSeTraducenA503() {
        server.expect(requestTo(BASE + "/checkout/preferences")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.crearCobro("ref-1", BigDecimal.TEN, "x"))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void respuestaSinEnlaceEsInesperada() {
        server.expect(requestTo(BASE + "/checkout/preferences"))
                .andRespond(withSuccess("{\"id\":\"123\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.crearCobro("ref-1", BigDecimal.TEN, "x"))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void encuentraElPagoAprobadoPorReferenciaExterna() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/payments/search")))
                .andExpect(queryParam("external_reference", "ref-1"))
                .andRespond(withSuccess("""
                        {"paging":{"total":2,"limit":30,"offset":0},
                         "results":[{"id":111,"status":"rejected"},{"id":222,"status":"approved"}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.buscarPagoAprobado("ref-1")).contains("222");
    }

    @Test
    void sinPagosAprobadosDevuelveVacio() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/payments/search")))
                .andRespond(withSuccess("{\"results\":[{\"id\":111,\"status\":\"in_process\"}]}", MediaType.APPLICATION_JSON));

        assertThat(client.buscarPagoAprobado("ref-1")).isEmpty();
    }

    @Test
    void proveedorCaidoAlVerificarSeTraduceAServicioNoDisponible() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/payments/search")))
                .andRespond(withException(new IOException("timeout")));

        assertThatThrownBy(() -> client.buscarPagoAprobado("ref-1")).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void busquedaConFormatoInesperadoSeTraduceAServicioNoDisponible() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/v1/payments/search")))
                .andRespond(withSuccess("{\"otra\":1}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.buscarPagoAprobado("ref-1")).isInstanceOf(ServicioNoDisponibleException.class);
    }
}

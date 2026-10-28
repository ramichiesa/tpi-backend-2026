package utnfc.isi.back.tpi.adopciones.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Contrato con Georef usando respuestas grabadas de la API real (27/10/2026), sin Internet.
 */
class GeorefClientTest {

    private static final String BASE = "https://apis.datos.gob.ar/georef/api/v2.0";

    private MockRestServiceServer server;
    private GeorefClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeorefClient(builder.build());
    }

    @Test
    void devuelveLasCoordenadasDeLaPrimeraDireccion() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/direcciones?")))
                .andExpect(queryParam("direccion", "Av%20Colon%201000"))
                .andExpect(queryParam("provincia", "Cordoba"))
                .andExpect(queryParam("max", "1"))
                .andRespond(withSuccess("""
                        {"cantidad":1,"direcciones":[{"altura":{"unidad":null,"valor":1000},
                         "nomenclatura":"AV COLON 1000, Córdoba, Capital, Córdoba",
                         "provincia":{"id":"14","nombre":"Córdoba"},
                         "ubicacion":{"lat":-31.409640332,"lon":-64.196633935}}],
                         "inicio":0,"total":88}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.geolocalizar("Av Colon 1000", "Cordoba", "Cordoba"))
                .contains(new Coordenadas(-31.409640332, -64.196633935));
        server.verify();
    }

    @Test
    void direccionInexistenteDevuelveVacio() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/direcciones")))
                .andRespond(withSuccess("{\"cantidad\":0,\"direcciones\":[],\"inicio\":0,\"total\":0}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.geolocalizar("Calle Inexistente 123456", "Cordoba", "Cordoba")).isEmpty();
    }

    @Test
    void direccionSinUbicacionDevuelveVacio() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/direcciones")))
                .andRespond(withSuccess("{\"cantidad\":1,\"direcciones\":[{\"ubicacion\":{\"lat\":null,\"lon\":null}}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.geolocalizar("Calle 1", "X", "Y")).isEmpty();
    }

    @Test
    void errorDelServidorSeTraduceAServicioNoDisponible() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/direcciones"))).andRespond(withServerError());

        assertThatThrownBy(() -> client.geolocalizar("Av Colon 1000", "Cordoba", "Cordoba"))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void parametroInvalidoSeTraduceAServicioNoDisponible() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/direcciones")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"errores\":[{\"codigo_interno\":1003,\"mensaje\":\"El parámetro 'direccion' es obligatorio.\"}]}"));

        assertThatThrownBy(() -> client.geolocalizar("", "Cordoba", "Cordoba"))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void respuestaConFormatoInesperadoSeTraduceAServicioNoDisponible() {
        server.expect(requestTo(org.hamcrest.Matchers.startsWith(BASE + "/direcciones")))
                .andRespond(withSuccess("{\"otra\":\"cosa\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.geolocalizar("Av Colon 1000", "Cordoba", "Cordoba"))
                .isInstanceOf(ServicioNoDisponibleException.class);
    }
}

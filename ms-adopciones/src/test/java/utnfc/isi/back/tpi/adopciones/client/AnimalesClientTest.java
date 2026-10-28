package utnfc.isi.back.tpi.adopciones.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import utnfc.isi.back.tpi.common.exception.ConflictoException;
import utnfc.isi.back.tpi.common.exception.RecursoNoEncontradoException;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Contrato HTTP con la API interna de ms-animales y traduccion de errores. */
class AnimalesClientTest {

    private static final String BASE = "http://ms-animales:8081";
    private static final String LUNA = """
            {"id":1,"nombre":"Luna","especie":"PERRO","edadMeses":24,"tamanio":"MEDIANO",
             "aptoNinios":true,"aptoOtrosAnimales":false,"requiereEspacioExterior":true,"estado":"DISPONIBLE"}
            """;

    private MockRestServiceServer server;
    private AnimalesClient client;
    private final ServiceTokenProvider tokens = mock(ServiceTokenProvider.class);

    @BeforeEach
    void setUp() {
        when(tokens.obtenerToken()).thenReturn("token-de-servicio");
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new AnimalesClient(builder.build(), tokens);
    }

    @Test
    void consultaElAnimalConElTokenDeServicio() {
        server.expect(requestTo(BASE + "/internal/animales/1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token-de-servicio"))
                .andRespond(withSuccess(LUNA, MediaType.APPLICATION_JSON));

        AnimalDto animal = client.obtener(1L);

        assertThat(animal.estado()).isEqualTo(EstadoAnimal.DISPONIBLE);
        assertThat(animal.aptoOtrosAnimales()).isFalse();
        assertThat(animal.requiereEspacioExterior()).isTrue();
        server.verify();
    }

    @Test
    void pideLaTransicionConElCuerpoDelContrato() {
        server.expect(requestTo(BASE + "/internal/animales/1/estado"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(content().json("{\"nuevoEstado\":\"RESERVADO\"}"))
                .andRespond(withSuccess(LUNA.replace("DISPONIBLE", "RESERVADO"), MediaType.APPLICATION_JSON));

        assertThat(client.cambiarEstado(1L, EstadoAnimal.RESERVADO).estado()).isEqualTo(EstadoAnimal.RESERVADO);
    }

    @Test
    void animalInexistenteSeTraduceA404() {
        server.expect(requestTo(BASE + "/internal/animales/99")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.obtener(99L)).isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void transicionInvalidaConservaElMensajeDeAnimales() {
        server.expect(requestTo(BASE + "/internal/animales/1/estado"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":409,\"message\":\"Transicion invalida para el animal 1: RESERVADO -> RESERVADO\"}"));

        assertThatThrownBy(() -> client.cambiarEstado(1L, EstadoAnimal.RESERVADO))
                .isInstanceOf(ConflictoException.class)
                .hasMessageContaining("RESERVADO -> RESERVADO");
    }

    @Test
    void errorInternoDeAnimalesSeTraduceA503() {
        server.expect(requestTo(BASE + "/internal/animales/1")).andRespond(withServerError());

        assertThatThrownBy(() -> client.obtener(1L)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void animalesCaidoSeTraduceA503() {
        server.expect(requestTo(BASE + "/internal/animales/1"))
                .andRespond(withException(new IOException("Connection refused")));

        assertThatThrownBy(() -> client.obtener(1L)).isInstanceOf(ServicioNoDisponibleException.class);
    }

    @Test
    void tokenRechazadoSeInvalidaYSeInforma503() {
        server.expect(requestTo(BASE + "/internal/animales/1")).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> client.obtener(1L)).isInstanceOf(ServicioNoDisponibleException.class);
        verify(tokens).invalidar();
    }

    @Test
    void estadoDesconocidoEsRespuestaInesperada() {
        server.expect(requestTo(BASE + "/internal/animales/1"))
                .andRespond(withSuccess(LUNA.replace("DISPONIBLE", "VOLANDO"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.obtener(1L)).isInstanceOf(ServicioNoDisponibleException.class);
    }
}

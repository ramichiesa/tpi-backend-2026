package utnfc.isi.back.tpi.donaciones;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;
import utnfc.isi.back.tpi.donaciones.client.MercadoPagoClient;
import utnfc.isi.back.tpi.donaciones.repository.DonacionRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Proceso P5 de punta a punta (HTTP + seguridad + reglas D-01 a D-04 + persistencia) con Mercado Pago simulado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FlujoDonacionIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private DonacionRepository repository;

    @MockitoBean
    private MercadoPagoClient mercadoPago;

    private static RequestPostProcessor adoptante(String sub) {
        return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_ADOPTANTE"));
    }

    private static RequestPostProcessor coordinador() {
        return jwt().jwt(j -> j.subject("coord-1")).authorities(new SimpleGrantedAuthority("ROLE_COORDINADOR"));
    }

    @BeforeEach
    void setUp() {
        when(mercadoPago.crearCobro(anyString(), any(), anyString()))
                .thenReturn(new MercadoPagoClient.Cobro("pref-1", "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-1"));
    }

    private long donar(String sub, String monto) throws Exception {
        String body = mvc.perform(post("/api/donaciones").with(adoptante(sub))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\":" + monto + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.moneda").value("ARS"))
                .andExpect(jsonPath("$.linkPago").value(org.hamcrest.Matchers.startsWith("https://")))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void donacionPendienteSeConfirmaCuandoElProveedorInformaUnPagoAprobado() throws Exception {
        long id = donar("ana", "500");

        when(mercadoPago.buscarPagoAprobado(anyString())).thenReturn(Optional.empty());
        mvc.perform(get("/api/donaciones/" + id).with(adoptante("ana")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.verificado").value(true));

        when(mercadoPago.buscarPagoAprobado(anyString())).thenReturn(Optional.of("987654"));
        mvc.perform(get("/api/donaciones/" + id).with(adoptante("ana")))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        mvc.perform(get("/api/donaciones/total").with(coordinador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalConfirmado").value(500.0))
                .andExpect(jsonPath("$.cantidad").value(1));
    }

    @Test
    void d03UnaDonacionConfirmadaNoVuelveAConsultarAlProveedor() throws Exception {
        long id = donar("ana", "100");
        when(mercadoPago.buscarPagoAprobado(anyString())).thenReturn(Optional.of("1"));
        mvc.perform(get("/api/donaciones/" + id).with(adoptante("ana"))).andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        when(mercadoPago.buscarPagoAprobado(anyString())).thenThrow(new ServicioNoDisponibleException("caido"));
        mvc.perform(get("/api/donaciones/" + id).with(adoptante("ana")))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.verificado").value(true));
    }

    @Test
    void d03UnMismoPagoNoConfirmaDosDonaciones() throws Exception {
        long primera = donar("ana", "100");
        long segunda = donar("ana", "200");
        when(mercadoPago.buscarPagoAprobado(anyString())).thenReturn(Optional.of("pago-repetido"));

        mvc.perform(get("/api/donaciones/" + primera).with(adoptante("ana"))).andExpect(jsonPath("$.estado").value("CONFIRMADA"));
        mvc.perform(get("/api/donaciones/" + segunda).with(adoptante("ana")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.verificado").value(false));
    }

    @Test
    void siElProveedorNoRespondeAlVerificarQuedaPendienteSinVerificar() throws Exception {
        long id = donar("ana", "500");
        when(mercadoPago.buscarPagoAprobado(anyString())).thenThrow(new ServicioNoDisponibleException("timeout"));

        mvc.perform(get("/api/donaciones/" + id).with(adoptante("ana")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.verificado").value(false));
    }

    @Test
    void siElProveedorNoRespondeAlCrearNoSeRegistraLaDonacion() throws Exception {
        when(mercadoPago.crearCobro(anyString(), any(), anyString()))
                .thenThrow(new ServicioNoDisponibleException("Mercado Pago no responde"));

        mvc.perform(post("/api/donaciones").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\":500}"))
                .andExpect(status().isServiceUnavailable());

        assertThat(repository.count()).isZero();
    }

    @Test
    void d01MontoMenorAlMinimoSeRechazaSinLlamarAlProveedor() throws Exception {
        mvc.perform(post("/api/donaciones").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\":0.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("D-01")));
        mvc.perform(post("/api/donaciones").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"monto\":-3}"))
                .andExpect(status().isBadRequest());

        verify(mercadoPago, never()).crearCobro(anyString(), any(), anyString());
    }

    @Test
    void d04CadaDonanteVeSoloLoSuyoYElCoordinadorVeTodo() throws Exception {
        long deAna = donar("ana", "100");
        donar("beto", "200");
        when(mercadoPago.buscarPagoAprobado(anyString())).thenReturn(Optional.empty());

        mvc.perform(get("/api/donaciones").with(adoptante("ana"))).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/donaciones").with(coordinador())).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/donaciones/" + deAna).with(adoptante("beto"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/donaciones/" + deAna).with(coordinador())).andExpect(status().isOk());
        mvc.perform(get("/api/donaciones/total").with(adoptante("ana"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/donaciones")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/donaciones/999").with(coordinador())).andExpect(status().isNotFound());
    }
}

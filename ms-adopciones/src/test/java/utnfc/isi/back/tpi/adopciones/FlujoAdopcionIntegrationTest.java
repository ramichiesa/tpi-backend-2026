package utnfc.isi.back.tpi.adopciones;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import utnfc.isi.back.tpi.adopciones.client.AnimalDto;
import utnfc.isi.back.tpi.adopciones.client.AnimalesClient;
import utnfc.isi.back.tpi.adopciones.client.Coordenadas;
import utnfc.isi.back.tpi.adopciones.client.EstadoAnimal;
import utnfc.isi.back.tpi.adopciones.client.GeorefClient;
import utnfc.isi.back.tpi.adopciones.service.TareaVencimientos;
import utnfc.isi.back.tpi.adopciones.service.SolicitudService;
import utnfc.isi.back.tpi.common.exception.ConflictoException;
import utnfc.isi.back.tpi.common.exception.ServicioNoDisponibleException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proceso central de punta a punta dentro de ms-adopciones: HTTP + seguridad + reglas +
 * persistencia (H2) + historial. Animales y Georef se reemplazan por dobles de prueba, de modo
 * que se puede verificar exactamente que transiciones se pidieron y simular sus fallas.
 * El reloj es controlable para probar plazos (R-09, R-11) sin esperar dias reales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FlujoAdopcionIntegrationTest {

    @TestConfiguration
    static class RelojDePrueba {
        @Bean
        @Primary
        RelojMovil relojMovil() {
            return new RelojMovil(Instant.parse("2026-10-27T13:00:00Z"));
        }
    }

    /** Reloj que el test puede adelantar. */
    static class RelojMovil extends Clock {
        private Instant ahora;

        RelojMovil(Instant inicio) {
            this.ahora = inicio;
        }

        void adelantar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RelojMovil reloj;

    @Autowired
    private SolicitudService solicitudService;

    @MockitoBean
    private AnimalesClient animales;

    @MockitoBean
    private GeorefClient georef;

    private static final String PERFIL = """
            {"nombreCompleto":"Ana Perez","telefono":"3515550000","provincia":"Cordoba","localidad":"Cordoba",
             "direccion":"Av Colon 1000","tipoVivienda":"CASA","tienePatio":%s,"hayNinios":%s,"hayOtrosAnimales":false}
            """;

    private static RequestPostProcessor adoptante(String sub) {
        return jwt().jwt(j -> j.subject(sub)).authorities(new SimpleGrantedAuthority("ROLE_ADOPTANTE"));
    }

    private static RequestPostProcessor coordinador() {
        return jwt().jwt(j -> j.subject("coord-1")).authorities(new SimpleGrantedAuthority("ROLE_COORDINADOR"));
    }

    private static AnimalDto animal(long id, EstadoAnimal estado, boolean aptoNinios, boolean exterior) {
        return new AnimalDto(id, "Luna", estado, aptoNinios, true, exterior);
    }

    @BeforeEach
    void setUp() {
        when(animales.obtener(1L)).thenReturn(animal(1, EstadoAnimal.DISPONIBLE, true, false));
        when(georef.geolocalizar(any(), any(), any())).thenReturn(Optional.of(new Coordenadas(-31.4096, -64.1966)));
    }

    private void completarPerfil(String sub, boolean patio, boolean ninios) throws Exception {
        mvc.perform(put("/api/adoptantes/me/perfil").with(adoptante(sub))
                        .contentType(MediaType.APPLICATION_JSON).content(PERFIL.formatted(patio, ninios)))
                .andExpect(status().isOk());
    }

    private long presentar(String sub, long animalId) throws Exception {
        String body = mvc.perform(post("/api/solicitudes").with(adoptante(sub))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":" + animalId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void procesoCompletoHastaLaAdopcionConfirmada() throws Exception {
        completarPerfil("ana", true, false);
        long id = presentar("ana", 1L);

        mvc.perform(get("/api/solicitudes/" + id).with(adoptante("ana")))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.distanciaKm").isNumber())
                .andExpect(jsonPath("$.requiereExcepcion").value(false))
                .andExpect(jsonPath("$.distanciaNoVerificada").value(false));

        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(coordinador())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("APROBADA"));

        mvc.perform(post("/api/solicitudes/" + id + "/confirmar-entrega").with(coordinador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("EN_ADAPTACION"))
                .andExpect(jsonPath("$.fechaControl").value("2026-11-26T13:00:00"));

        // R-11: antes de la fecha de control no se puede registrar
        mvc.perform(post("/api/solicitudes/" + id + "/control-final").with(coordinador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"resultado\":\"CONFORME\"}"))
                .andExpect(status().isConflict());

        reloj.adelantar(Duration.ofDays(30));
        mvc.perform(post("/api/solicitudes/" + id + "/control-final").with(coordinador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"CONFORME\",\"observaciones\":\"Se adapto muy bien\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("FINALIZADA"))
                .andExpect(jsonPath("$.resultadoControl").value("CONFORME"));

        // Colaboracion con Animales: cada transicion se pidio en orden
        InOrder orden = inOrder(animales);
        orden.verify(animales).cambiarEstado(1L, EstadoAnimal.RESERVADO);
        orden.verify(animales).cambiarEstado(1L, EstadoAnimal.EN_ADAPTACION);
        orden.verify(animales).cambiarEstado(1L, EstadoAnimal.ADOPTADO);

        mvc.perform(get("/api/solicitudes/" + id + "/historial").with(adoptante("ana")))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[1].actor").value("coord-1"))
                .andExpect(jsonPath("$[3].estadoNuevo").value("FINALIZADA"));
    }

    @Test
    void r03SinPerfilNoSePuedeSolicitar() throws Exception {
        mvc.perform(post("/api/solicitudes").with(adoptante("sin-perfil"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("R-03")));
    }

    @Test
    void r02UnaSegundaSolicitudActivaSeRechaza() throws Exception {
        when(animales.obtener(2L)).thenReturn(animal(2, EstadoAnimal.DISPONIBLE, true, false));
        completarPerfil("ana", true, false);
        presentar("ana", 1L);

        mvc.perform(post("/api/solicitudes").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("R-02")));
    }

    @Test
    void r01UnAnimalConSolicitudPendienteNoRecibeOtra() throws Exception {
        completarPerfil("ana", true, false);
        completarPerfil("beto", true, false);
        presentar("ana", 1L);

        mvc.perform(post("/api/solicitudes").with(adoptante("beto"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("R-01")));
    }

    @Test
    void r01UnAnimalNoDisponibleNoRecibeSolicitudes() throws Exception {
        when(animales.obtener(4L)).thenReturn(animal(4, EstadoAnimal.NO_DISPONIBLE, true, false));
        completarPerfil("ana", true, false);

        mvc.perform(post("/api/solicitudes").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":4}"))
                .andExpect(status().isConflict());
    }

    @Test
    void r04IncompatibilidadQuedaRechazadaAutomaticamente() throws Exception {
        when(animales.obtener(3L)).thenReturn(animal(3, EstadoAnimal.DISPONIBLE, false, false));
        completarPerfil("ana", true, true);

        mvc.perform(post("/api/solicitudes").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value(org.hamcrest.Matchers.containsString("R-04")));
        // La incompatibilidad se detecta antes de consultar Georef
        verify(georef, never()).geolocalizar(any(), any(), any());
    }

    @Test
    void r05YR06FallaDeGeorefNoImpideLaSolicitud() throws Exception {
        when(animales.obtener(1L)).thenReturn(animal(1, EstadoAnimal.DISPONIBLE, true, true));
        when(georef.geolocalizar(any(), any(), any())).thenThrow(new ServicioNoDisponibleException("timeout"));
        completarPerfil("ana", false, false);

        mvc.perform(post("/api/solicitudes").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.distanciaNoVerificada").value(true))
                .andExpect(jsonPath("$.observaciones.length()").value(2));
    }

    @Test
    void r06YR08LejosDelRefugioExigeJustificacionParaAprobar() throws Exception {
        when(georef.geolocalizar(any(), any(), any())).thenReturn(Optional.of(new Coordenadas(-34.6037, -58.3816)));
        completarPerfil("ana", true, false);
        long id = presentar("ana", 1L);

        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(coordinador())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("R-08")));
        verify(animales, never()).cambiarEstado(anyLong(), any());

        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(coordinador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"justificacion\":\"Traslado garantizado\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.justificacionExcepcion").value("Traslado garantizado"));
    }

    @Test
    void siAnimalesNoRespondeAlAprobarLaSolicitudSiguePendiente() throws Exception {
        completarPerfil("ana", true, false);
        long id = presentar("ana", 1L);
        when(animales.cambiarEstado(1L, EstadoAnimal.RESERVADO))
                .thenThrow(new ServicioNoDisponibleException("El servicio de Animales no responde"));

        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(coordinador())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());

        mvc.perform(get("/api/solicitudes/" + id).with(coordinador()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void r07SiAnimalesRechazaLaReservaNoSeAprueba() throws Exception {
        completarPerfil("ana", true, false);
        long id = presentar("ana", 1L);
        when(animales.cambiarEstado(1L, EstadoAnimal.RESERVADO))
                .thenThrow(new ConflictoException("Transicion invalida para el animal 1: NO_DISPONIBLE -> RESERVADO"));

        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(coordinador())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/solicitudes/" + id).with(coordinador()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void siAnimalesNoRespondeAlPresentarSeInforma503() throws Exception {
        when(animales.obtener(1L)).thenThrow(new ServicioNoDisponibleException("El servicio de Animales no responde"));
        completarPerfil("ana", true, false);

        mvc.perform(post("/api/solicitudes").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"animalId\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void r09LaTareaProgramadaCancelaLasEntregasVencidasYLiberaAlAnimal() throws Exception {
        completarPerfil("ana", true, false);
        long id = presentar("ana", 1L);
        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(coordinador())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        reloj.adelantar(Duration.ofDays(8));
        new TareaVencimientos(solicitudService).vencerEntregas();

        mvc.perform(get("/api/solicitudes/" + id).with(adoptante("ana")))
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.motivoCierre").value(org.hamcrest.Matchers.containsString("R-09")));
        verify(animales).cambiarEstado(1L, EstadoAnimal.DISPONIBLE);
    }

    @Test
    void r09ConfirmarFueraDePlazoCancelaEInforma409() throws Exception {
        completarPerfil("ana", true, false);
        long id = presentar("ana", 1L);
        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(coordinador())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());

        reloj.adelantar(Duration.ofDays(8));
        mvc.perform(post("/api/solicitudes/" + id + "/confirmar-entrega").with(coordinador()))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/solicitudes/" + id).with(coordinador()))
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
        verify(animales, never()).cambiarEstado(1L, EstadoAnimal.EN_ADAPTACION);
    }

    @Test
    void r12DevolucionDelAdoptanteRevierteYLiberaAlAnimal() throws Exception {
        completarPerfil("ana", true, false);
        long id = presentar("ana", 1L);
        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(coordinador())
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post("/api/solicitudes/" + id + "/confirmar-entrega").with(coordinador())).andExpect(status().isOk());

        mvc.perform(post("/api/solicitudes/" + id + "/devolucion").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Me mudo al exterior\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("REVERTIDA"));
        verify(animales).cambiarEstado(1L, EstadoAnimal.DISPONIBLE);
    }

    @Test
    void seguridadRolesYPertenencia() throws Exception {
        completarPerfil("ana", true, false);
        long id = presentar("ana", 1L);

        // Sin token
        mvc.perform(get("/api/solicitudes")).andExpect(status().isUnauthorized());
        // Un adoptante no puede aprobar
        mvc.perform(post("/api/solicitudes/" + id + "/aprobar").with(adoptante("ana"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        // Otro adoptante no ve ni cancela una solicitud ajena
        mvc.perform(get("/api/solicitudes/" + id).with(adoptante("beto"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/solicitudes/" + id + "/cancelar").with(adoptante("beto"))).andExpect(status().isForbidden());
        // Cada adoptante lista solo las suyas; el Coordinador ve todas
        mvc.perform(get("/api/solicitudes").with(adoptante("beto"))).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/solicitudes").with(coordinador())).andExpect(jsonPath("$.length()").value(1));
        // El Coordinador puede ver el perfil del postulante; un adoptante no
        mvc.perform(get("/api/adoptantes/ana/perfil").with(coordinador())).andExpect(status().isOk());
        mvc.perform(get("/api/adoptantes/ana/perfil").with(adoptante("beto"))).andExpect(status().isForbidden());
        // El duenio puede cancelar su solicitud pendiente
        mvc.perform(post("/api/solicitudes/" + id + "/cancelar").with(adoptante("ana")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void validacionesDeEntrada() throws Exception {
        mvc.perform(put("/api/adoptantes/me/perfil").with(adoptante("ana"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nombreCompleto\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores.length()").value(org.hamcrest.Matchers.greaterThan(3)));

        mvc.perform(post("/api/solicitudes/1/rechazar").with(coordinador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/solicitudes/999").with(coordinador())).andExpect(status().isNotFound());
        verify(animales, never()).cambiarEstado(eq(999L), any());
    }
}

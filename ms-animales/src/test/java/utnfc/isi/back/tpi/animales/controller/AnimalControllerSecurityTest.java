package utnfc.isi.back.tpi.animales.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import utnfc.isi.back.tpi.animales.config.SecurityConfig;
import utnfc.isi.back.tpi.animales.dto.AnimalResponse;
import utnfc.isi.back.tpi.animales.model.EstadoAnimal;
import utnfc.isi.back.tpi.animales.model.Especie;
import utnfc.isi.back.tpi.animales.model.Tamanio;
import utnfc.isi.back.tpi.animales.service.AnimalService;
import utnfc.isi.back.tpi.common.exception.ConflictoException;
import utnfc.isi.back.tpi.common.security.JsonAccessDeniedHandler;
import utnfc.isi.back.tpi.common.security.JsonAuthenticationEntryPoint;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Matriz de permisos de ms-animales (CONTRATOS.md 3.5) y validaciones de entrada.
 * El servicio se reemplaza por un doble: aca solo interesa la capa web y la seguridad.
 */
@WebMvcTest(controllers = {AnimalController.class, AnimalInternoController.class})
@Import({SecurityConfig.class, JsonAuthenticationEntryPoint.class, JsonAccessDeniedHandler.class})
class AnimalControllerSecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AnimalService service;

    private static final String ANIMAL_JSON = """
            {"nombre":"Luna","especie":"PERRO","edadMeses":24,"tamanio":"MEDIANO",
             "aptoNinios":true,"aptoOtrosAnimales":true,"requiereEspacioExterior":false}
            """;

    private static RequestPostProcessor con(String rol) {
        return jwt().jwt(j -> j.subject("usuario-" + rol)).authorities(new SimpleGrantedAuthority("ROLE_" + rol));
    }

    private static AnimalResponse luna(EstadoAnimal estado) {
        return new AnimalResponse(1L, "Luna", Especie.PERRO, 24, Tamanio.MEDIANO, null, true, true, false, estado, null);
    }

    @Test
    void sinTokenDevuelve401ConFormatoUnico() throws Exception {
        mvc.perform(get("/api/animales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.path").value("/api/animales"));
    }

    @Test
    void adoptantePuedeConsultarElCatalogo() throws Exception {
        when(service.listar(null, null)).thenReturn(List.of(luna(EstadoAnimal.DISPONIBLE)));

        mvc.perform(get("/api/animales").with(con("ADOPTANTE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Luna"));
    }

    @Test
    void adoptanteNoPuedeDarDeAltaAnimales() throws Exception {
        mvc.perform(post("/api/animales").with(con("ADOPTANTE"))
                        .contentType(MediaType.APPLICATION_JSON).content(ANIMAL_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void coordinadorCreaAnimalYRecibe201ConLocation() throws Exception {
        when(service.crear(any())).thenReturn(luna(EstadoAnimal.DISPONIBLE));

        mvc.perform(post("/api/animales").with(con("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(ANIMAL_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/animales/1"))
                .andExpect(jsonPath("$.estado").value("DISPONIBLE"));
    }

    @Test
    void altaConDatosInvalidosDevuelve400ConCampos() throws Exception {
        mvc.perform(post("/api/animales").with(con("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"especie\":\"PERRO\",\"edadMeses\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores").isArray());
    }

    @Test
    void r13SeTraduceA409() throws Exception {
        when(service.marcarNoDisponible(eq(1L), any()))
                .thenThrow(new ConflictoException("El animal 1 tiene una adopcion en curso"));

        mvc.perform(post("/api/animales/1/marcar-no-disponible").with(con("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Cuarentena\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflicto"));
    }

    @Test
    void apiInternaRechazaUsuariosHumanos() throws Exception {
        mvc.perform(put("/internal/animales/1/estado").with(con("COORDINADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoEstado\":\"RESERVADO\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void apiInternaAceptaElTokenDeServicio() throws Exception {
        when(service.cambiarEstadoPorProceso(1L, EstadoAnimal.RESERVADO)).thenReturn(luna(EstadoAnimal.RESERVADO));

        mvc.perform(put("/internal/animales/1/estado").with(con("SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoEstado\":\"RESERVADO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESERVADO"));
    }

    @Test
    void elTokenDeServicioNoUsaLaApiPublica() throws Exception {
        mvc.perform(get("/api/animales").with(con("SERVICIO")))
                .andExpect(status().isForbidden());
    }

    @Test
    void estadoInexistenteEnLaApiInternaDevuelve400() throws Exception {
        mvc.perform(put("/internal/animales/1/estado").with(con("SERVICIO"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"nuevoEstado\":\"VOLANDO\"}"))
                .andExpect(status().isBadRequest());
    }
}

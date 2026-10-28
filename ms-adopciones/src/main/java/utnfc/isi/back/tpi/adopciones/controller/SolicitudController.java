package utnfc.isi.back.tpi.adopciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import utnfc.isi.back.tpi.adopciones.dto.EventoResponse;
import utnfc.isi.back.tpi.adopciones.dto.SolicitudRequests;
import utnfc.isi.back.tpi.adopciones.dto.SolicitudResponse;
import utnfc.isi.back.tpi.adopciones.model.EstadoSolicitud;
import utnfc.isi.back.tpi.adopciones.service.SolicitudService;

import java.net.URI;
import java.util.List;

/**
 * Proceso de adopcion de punta a punta (CONTRATOS.md, seccion 7). El controlador solo traduce
 * HTTP: las reglas estan en SolicitudService y en la entidad Solicitud; los roles, en SecurityConfig.
 */
@RestController
@RequestMapping("/api/solicitudes")
@Tag(name = "Solicitudes", description = "Presentacion, evaluacion, entrega y seguimiento (P2 a P4)")
public class SolicitudController {

    private final SolicitudService service;

    public SolicitudController(SolicitudService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Presenta una solicitud (ADOPTANTE). Aplica R-01 a R-06",
            description = "201 PENDIENTE con marcas, o 201 RECHAZADA si hay incompatibilidad dura (R-04)")
    @ApiResponse(responseCode = "201", description = "Solicitud registrada")
    @ApiResponse(responseCode = "409", description = "R-01, R-02 o R-03")
    @ApiResponse(responseCode = "503", description = "El servicio de Animales no responde")
    public ResponseEntity<SolicitudResponse> crear(JwtAuthenticationToken auth,
                                                   @Valid @RequestBody SolicitudRequests.Crear request) {
        SolicitudResponse creada = service.crear(UsuarioActual.de(auth), request.animalId());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @GetMapping
    @Operation(summary = "Lista solicitudes: las propias (ADOPTANTE) o todas (COORDINADOR)")
    public List<SolicitudResponse> listar(JwtAuthenticationToken auth,
                                          @RequestParam(required = false) EstadoSolicitud estado) {
        return service.listar(UsuarioActual.de(auth), estado);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta una solicitud (duenio o COORDINADOR)")
    @ApiResponse(responseCode = "403", description = "Pertenece a otro adoptante")
    public SolicitudResponse obtener(JwtAuthenticationToken auth, @PathVariable Long id) {
        return service.obtener(UsuarioActual.de(auth), id);
    }

    @GetMapping("/{id}/historial")
    @Operation(summary = "Historial de cambios de estado con fecha, actor y motivo (duenio o COORDINADOR)")
    public List<EventoResponse> historial(JwtAuthenticationToken auth, @PathVariable Long id) {
        return service.historial(UsuarioActual.de(auth), id);
    }

    @PostMapping("/{id}/cancelar")
    @Operation(summary = "El adoptante cancela su solicitud PENDIENTE")
    public SolicitudResponse cancelar(JwtAuthenticationToken auth, @PathVariable Long id) {
        return service.cancelar(UsuarioActual.de(auth), id);
    }

    @PostMapping("/{id}/aprobar")
    @Operation(summary = "Aprueba y reserva al animal (COORDINADOR). R-07, R-08")
    @ApiResponse(responseCode = "409", description = "No esta PENDIENTE, falta justificacion o el animal no esta DISPONIBLE")
    @ApiResponse(responseCode = "503", description = "Animales no responde: la solicitud sigue PENDIENTE")
    public SolicitudResponse aprobar(JwtAuthenticationToken auth, @PathVariable Long id,
                                     @Valid @RequestBody(required = false) SolicitudRequests.Aprobar request) {
        String justificacion = request == null ? null : request.justificacion();
        return service.aprobar(UsuarioActual.de(auth), id, justificacion);
    }

    @PostMapping("/{id}/rechazar")
    @Operation(summary = "Rechaza con motivo obligatorio (COORDINADOR). R-08")
    public SolicitudResponse rechazar(JwtAuthenticationToken auth, @PathVariable Long id,
                                      @Valid @RequestBody SolicitudRequests.Motivo request) {
        return service.rechazar(UsuarioActual.de(auth), id, request.motivo());
    }

    @PostMapping("/{id}/confirmar-entrega")
    @Operation(summary = "Confirma la entrega; animal y solicitud pasan a EN_ADAPTACION (COORDINADOR). R-09, R-10")
    public SolicitudResponse confirmarEntrega(JwtAuthenticationToken auth, @PathVariable Long id) {
        return service.confirmarEntrega(UsuarioActual.de(auth), id);
    }

    @PostMapping("/{id}/cancelar-entrega")
    @Operation(summary = "Cancela una entrega pendiente; el animal vuelve a DISPONIBLE (COORDINADOR)")
    public SolicitudResponse cancelarEntrega(JwtAuthenticationToken auth, @PathVariable Long id,
                                             @Valid @RequestBody SolicitudRequests.Motivo request) {
        return service.cancelarEntrega(UsuarioActual.de(auth), id, request.motivo());
    }

    @PostMapping("/{id}/control-final")
    @Operation(summary = "Registra el control final (COORDINADOR). R-11, R-12")
    @ApiResponse(responseCode = "409", description = "Anterior a la fecha de control o no esta EN_ADAPTACION")
    public SolicitudResponse controlFinal(JwtAuthenticationToken auth, @PathVariable Long id,
                                          @Valid @RequestBody SolicitudRequests.ControlFinal request) {
        return service.registrarControl(UsuarioActual.de(auth), id, request.resultado(), request.observaciones());
    }

    @PostMapping("/{id}/devolucion")
    @Operation(summary = "El adoptante devuelve al animal durante la adaptacion. R-12")
    public SolicitudResponse devolucion(JwtAuthenticationToken auth, @PathVariable Long id,
                                        @Valid @RequestBody SolicitudRequests.Motivo request) {
        return service.devolver(UsuarioActual.de(auth), id, request.motivo());
    }
}

package utnfc.isi.back.tpi.adopciones.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import utnfc.isi.back.tpi.adopciones.dto.PerfilRequest;
import utnfc.isi.back.tpi.adopciones.dto.PerfilResponse;
import utnfc.isi.back.tpi.adopciones.service.PerfilService;

/**
 * Perfil del adoptante. El duenio se toma SIEMPRE del token ("me"), nunca de la URL ni del
 * cuerpo, de modo que nadie puede escribir el perfil de otra persona.
 */
@RestController
@RequestMapping("/api/adoptantes")
@Tag(name = "Perfiles", description = "Perfil y condiciones del hogar del adoptante (R-03)")
public class PerfilController {

    private final PerfilService service;

    public PerfilController(PerfilService service) {
        this.service = service;
    }

    @PutMapping("/me/perfil")
    @Operation(summary = "Crea o reemplaza el perfil propio (ADOPTANTE)")
    @ApiResponse(responseCode = "400", description = "Faltan datos o son invalidos")
    public PerfilResponse guardar(JwtAuthenticationToken auth, @Valid @RequestBody PerfilRequest request) {
        return service.guardar(auth.getToken().getSubject(), request);
    }

    @GetMapping("/me/perfil")
    @Operation(summary = "Consulta el perfil propio (ADOPTANTE)")
    @ApiResponse(responseCode = "404", description = "Todavia no lo completo")
    public PerfilResponse obtenerPropio(JwtAuthenticationToken auth) {
        return service.obtener(auth.getToken().getSubject());
    }

    @GetMapping("/{adoptanteId}/perfil")
    @Operation(summary = "Consulta el perfil de un postulante para evaluar su solicitud (COORDINADOR)")
    public PerfilResponse obtener(@PathVariable String adoptanteId) {
        return service.obtener(adoptanteId);
    }
}

package utnfc.isi.back.tpi.donaciones.controller;

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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import utnfc.isi.back.tpi.common.security.Roles;
import utnfc.isi.back.tpi.donaciones.dto.DonacionDtos.CrearDonacion;
import utnfc.isi.back.tpi.donaciones.dto.DonacionDtos.DonacionResponse;
import utnfc.isi.back.tpi.donaciones.dto.DonacionDtos.TotalResponse;
import utnfc.isi.back.tpi.donaciones.service.DonacionService;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/donaciones")
@Tag(name = "Donaciones", description = "Donaciones a la cuenta comun con Mercado Pago (P5)")
public class DonacionController {

    private final DonacionService service;

    public DonacionController(DonacionService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Crea una donacion y devuelve el enlace de pago (ADOPTANTE)")
    @ApiResponse(responseCode = "201", description = "Donacion PENDIENTE con linkPago")
    @ApiResponse(responseCode = "400", description = "Monto menor al minimo (D-01)")
    @ApiResponse(responseCode = "503", description = "Mercado Pago no responde: no se crea la donacion")
    public ResponseEntity<DonacionResponse> crear(JwtAuthenticationToken auth, @Valid @RequestBody CrearDonacion request) {
        DonacionResponse creada = service.crear(auth.getToken().getSubject(), request.monto());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creada.id()).toUri();
        return ResponseEntity.created(location).body(creada);
    }

    @GetMapping
    @Operation(summary = "Lista las donaciones propias (ADOPTANTE) o todas (COORDINADOR)")
    public List<DonacionResponse> listar(JwtAuthenticationToken auth) {
        return service.listar(auth.getToken().getSubject(), esCoordinador(auth));
    }

    @GetMapping("/total")
    @Operation(summary = "Total confirmado y cantidad de donaciones confirmadas (COORDINADOR)")
    public TotalResponse total() {
        return service.total();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta una donacion; si esta PENDIENTE verifica el pago en Mercado Pago (D-02)")
    @ApiResponse(responseCode = "403", description = "Pertenece a otro donante (D-04)")
    public DonacionResponse obtener(JwtAuthenticationToken auth, @PathVariable Long id) {
        return service.obtener(auth.getToken().getSubject(), esCoordinador(auth), id);
    }

    private static boolean esCoordinador(JwtAuthenticationToken auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + Roles.COORDINADOR));
    }
}

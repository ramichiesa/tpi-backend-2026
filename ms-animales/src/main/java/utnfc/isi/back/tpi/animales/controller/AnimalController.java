package utnfc.isi.back.tpi.animales.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import utnfc.isi.back.tpi.animales.dto.AnimalRequest;
import utnfc.isi.back.tpi.animales.dto.AnimalResponse;
import utnfc.isi.back.tpi.animales.dto.MotivoRequest;
import utnfc.isi.back.tpi.animales.model.EstadoAnimal;
import utnfc.isi.back.tpi.animales.model.Especie;
import utnfc.isi.back.tpi.animales.service.AnimalService;

import java.net.URI;
import java.util.List;

/**
 * API publica del catalogo (se publica por el Gateway en /api/animales/**).
 * Los permisos por rol estan en SecurityConfig.
 */
@RestController
@RequestMapping("/api/animales")
@Tag(name = "Animales", description = "Catalogo de animales y su disponibilidad (P1)")
public class AnimalController {

    private final AnimalService service;

    public AnimalController(AnimalService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Alta de un animal (COORDINADOR). Queda DISPONIBLE")
    @ApiResponse(responseCode = "201", description = "Animal creado")
    @ApiResponse(responseCode = "400", description = "Datos invalidos")
    public ResponseEntity<AnimalResponse> crear(@Valid @RequestBody AnimalRequest request) {
        AnimalResponse creado = service.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(creado.id()).toUri();
        return ResponseEntity.created(location).body(creado);
    }

    @GetMapping
    @Operation(summary = "Lista animales, con filtros opcionales por estado y especie")
    public List<AnimalResponse> listar(@RequestParam(required = false) EstadoAnimal estado,
                                       @RequestParam(required = false) Especie especie) {
        return service.listar(estado, especie);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta un animal")
    @ApiResponse(responseCode = "404", description = "No existe el animal")
    public AnimalResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifica los datos de un animal (COORDINADOR). No cambia su estado")
    public AnimalResponse actualizar(@PathVariable Long id, @Valid @RequestBody AnimalRequest request) {
        return service.actualizar(id, request);
    }

    @PostMapping("/{id}/marcar-no-disponible")
    @Operation(summary = "Deja al animal NO_DISPONIBLE con motivo (COORDINADOR)")
    @ApiResponse(responseCode = "409", description = "Tiene una adopcion en curso (R-13) o no esta DISPONIBLE")
    public AnimalResponse marcarNoDisponible(@PathVariable Long id, @Valid @RequestBody MotivoRequest request) {
        return service.marcarNoDisponible(id, request.motivo());
    }

    @PostMapping("/{id}/marcar-disponible")
    @Operation(summary = "Devuelve a DISPONIBLE un animal NO_DISPONIBLE (COORDINADOR)")
    @ApiResponse(responseCode = "409", description = "El animal no esta NO_DISPONIBLE")
    public AnimalResponse marcarDisponible(@PathVariable Long id) {
        return service.marcarDisponible(id);
    }
}

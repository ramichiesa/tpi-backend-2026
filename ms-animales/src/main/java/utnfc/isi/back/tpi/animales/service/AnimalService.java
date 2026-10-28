package utnfc.isi.back.tpi.animales.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import utnfc.isi.back.tpi.animales.dto.AnimalRequest;
import utnfc.isi.back.tpi.animales.dto.AnimalResponse;
import utnfc.isi.back.tpi.animales.model.Animal;
import utnfc.isi.back.tpi.animales.model.EstadoAnimal;
import utnfc.isi.back.tpi.animales.model.Especie;
import utnfc.isi.back.tpi.animales.repository.AnimalRepository;
import utnfc.isi.back.tpi.common.exception.RecursoNoEncontradoException;

import java.util.List;

/**
 * Casos de uso del catalogo. Las reglas de estado viven en la entidad {@link Animal};
 * el servicio coordina la carga, la transaccion y la traduccion a DTO.
 */
@Service
public class AnimalService {

    private static final Logger log = LoggerFactory.getLogger(AnimalService.class);

    private final AnimalRepository repository;

    public AnimalService(AnimalRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AnimalResponse crear(AnimalRequest req) {
        Animal animal = new Animal(req.nombre(), req.especie(), req.edadMeses(), req.tamanio(),
                req.necesidadesEspeciales(), req.aptoNinios(), req.aptoOtrosAnimales(), req.requiereEspacioExterior());
        Animal guardado = repository.save(animal);
        log.info("Alta del animal {} ({})", guardado.getId(), guardado.getNombre());
        return AnimalResponse.desde(guardado);
    }

    @Transactional(readOnly = true)
    public List<AnimalResponse> listar(EstadoAnimal estado, Especie especie) {
        List<Animal> animales;
        if (estado != null && especie != null) {
            animales = repository.findByEstadoAndEspecieOrderByIdAsc(estado, especie);
        } else if (estado != null) {
            animales = repository.findByEstadoOrderByIdAsc(estado);
        } else if (especie != null) {
            animales = repository.findByEspecieOrderByIdAsc(especie);
        } else {
            animales = repository.findAllByOrderByIdAsc();
        }
        return animales.stream().map(AnimalResponse::desde).toList();
    }

    @Transactional(readOnly = true)
    public AnimalResponse obtener(Long id) {
        return AnimalResponse.desde(buscar(id));
    }

    @Transactional
    public AnimalResponse actualizar(Long id, AnimalRequest req) {
        Animal animal = buscar(id);
        animal.actualizarDatos(req.nombre(), req.especie(), req.edadMeses(), req.tamanio(),
                req.necesidadesEspeciales(), req.aptoNinios(), req.aptoOtrosAnimales(), req.requiereEspacioExterior());
        return AnimalResponse.desde(animal);
    }

    @Transactional
    public AnimalResponse marcarNoDisponible(Long id, String motivo) {
        Animal animal = buscar(id);
        animal.marcarNoDisponible(motivo);
        log.info("Animal {} marcado NO_DISPONIBLE", id);
        return AnimalResponse.desde(animal);
    }

    @Transactional
    public AnimalResponse marcarDisponible(Long id) {
        Animal animal = buscar(id);
        animal.marcarDisponible();
        log.info("Animal {} vuelve a DISPONIBLE", id);
        return AnimalResponse.desde(animal);
    }

    /**
     * API interna: el servicio de Adopciones pide llevar al animal a otro estado. Si la
     * transicion no es valida la entidad lanza ConflictoException (409) y no se modifica nada.
     * saveAndFlush fuerza el chequeo de version dentro de esta transaccion: si otra operacion
     * cambio el animal al mismo tiempo, se responde 409 y no se confirma un estado pisado.
     */
    @Transactional
    public AnimalResponse cambiarEstadoPorProceso(Long id, EstadoAnimal nuevoEstado) {
        Animal animal = buscar(id);
        EstadoAnimal anterior = animal.getEstado();
        animal.cambiarEstadoPorProceso(nuevoEstado);
        repository.saveAndFlush(animal);
        log.info("Animal {}: {} -> {} (pedido por el proceso de adopcion)", id, anterior, nuevoEstado);
        return AnimalResponse.desde(animal);
    }

    private Animal buscar(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el animal " + id));
    }
}

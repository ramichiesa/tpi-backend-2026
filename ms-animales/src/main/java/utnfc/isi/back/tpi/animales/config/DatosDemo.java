package utnfc.isi.back.tpi.animales.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import utnfc.isi.back.tpi.animales.model.Animal;
import utnfc.isi.back.tpi.animales.model.Especie;
import utnfc.isi.back.tpi.animales.model.Tamanio;
import utnfc.isi.back.tpi.animales.repository.AnimalRepository;

import java.util.List;

/**
 * Carga un catalogo inicial SOLO si la tabla esta vacia y la propiedad animales.datos-demo
 * es verdadera. Sirve para que la demostracion no dependa de cargas manuales improvisadas.
 * Cada animal esta pensado para mostrar una regla distinta (ver docs/DEFENSA.md).
 */
@Component
@ConditionalOnProperty(name = "animales.datos-demo", havingValue = "true")
public class DatosDemo implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatosDemo.class);

    private final AnimalRepository repository;

    public DatosDemo(AnimalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        Animal rocco = new Animal("Rocco", Especie.PERRO, 60, Tamanio.GRANDE, "Recuperacion de una cirugia",
                true, true, false);
        rocco.marcarNoDisponible("Tratamiento veterinario en curso");
        repository.saveAll(List.of(
                // Compatible con casi cualquier hogar: camino feliz
                new Animal("Luna", Especie.PERRO, 24, Tamanio.MEDIANO, null, true, true, false),
                // Necesita patio (R-05: observacion) y no convive con otros animales (R-04)
                new Animal("Toby", Especie.PERRO, 36, Tamanio.GRANDE, null, true, false, true),
                // No apto con ninios (R-04: rechazo automatico si el hogar tiene ninios)
                new Animal("Michi", Especie.GATO, 12, Tamanio.PEQUENIO, "Asustadizo", false, true, false),
                // NO_DISPONIBLE: no puede recibir solicitudes (R-01)
                rocco));
        log.info("Se cargaron 4 animales de demostracion");
    }
}

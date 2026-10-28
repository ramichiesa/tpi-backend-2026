package utnfc.isi.back.tpi.animales;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import utnfc.isi.back.tpi.animales.model.EstadoAnimal;
import utnfc.isi.back.tpi.animales.repository.AnimalRepository;

import static org.assertj.core.api.Assertions.assertThat;

/** El contexto completo arranca (seguridad, JPA, OpenAPI, JAR comun) y carga los datos de demostracion. */
@SpringBootTest(properties = "animales.datos-demo=true")
@ActiveProfiles("test")
class AnimalesApplicationTest {

    @Autowired
    private AnimalRepository repository;

    @Test
    void arrancaYCargaElCatalogoDeDemostracion() {
        assertThat(repository.count()).isEqualTo(4);
        assertThat(repository.findByEstadoOrderByIdAsc(EstadoAnimal.NO_DISPONIBLE)).hasSize(1);
    }
}

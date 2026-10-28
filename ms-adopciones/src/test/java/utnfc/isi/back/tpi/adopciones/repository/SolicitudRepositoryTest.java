package utnfc.isi.back.tpi.adopciones.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import utnfc.isi.back.tpi.adopciones.model.EstadoSolicitud;
import utnfc.isi.back.tpi.adopciones.model.Solicitud;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Las restricciones UNIQUE de las columnas de bloqueo son la ultima linea de defensa de R-01 y
 * R-02 ante peticiones simultaneas: aunque el servicio no detectara el conflicto, la base lo impide.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class SolicitudRepositoryTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 27, 10, 0);

    @Autowired
    private SolicitudRepository repository;

    private static Solicitud pendiente(Long animalId, String adoptante) {
        return Solicitud.pendiente(animalId, "Luna", adoptante, AHORA, 5.0, false, false, List.of("obs"));
    }

    @Test
    void r01LaBaseImpideDosSolicitudesPendientesSobreElMismoAnimal() {
        repository.saveAndFlush(pendiente(1L, "ana"));

        assertThatThrownBy(() -> repository.saveAndFlush(pendiente(1L, "beto")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void r02LaBaseImpideDosSolicitudesActivasDelMismoAdoptante() {
        repository.saveAndFlush(pendiente(1L, "ana"));

        assertThatThrownBy(() -> repository.saveAndFlush(pendiente(2L, "ana")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unaSolicitudCerradaYaNoBloquea() {
        Solicitud primera = repository.saveAndFlush(pendiente(1L, "ana"));
        primera.rechazar("No corresponde", AHORA, "coord");
        repository.saveAndFlush(primera);

        Solicitud segunda = repository.saveAndFlush(pendiente(1L, "ana"));

        assertThat(segunda.getId()).isNotNull();
        assertThat(repository.existsByAnimalIdAndEstado(1L, EstadoSolicitud.PENDIENTE)).isTrue();
        assertThat(repository.existsByAdoptanteIdAndEstadoIn("ana",
                EnumSet.of(EstadoSolicitud.PENDIENTE, EstadoSolicitud.APROBADA, EstadoSolicitud.EN_ADAPTACION))).isTrue();
    }

    @Test
    void persisteObservacionesEHistorial() {
        Solicitud s = repository.saveAndFlush(pendiente(1L, "ana"));

        Solicitud leida = repository.findById(s.getId()).orElseThrow();

        assertThat(leida.getObservaciones()).containsExactly("obs");
        assertThat(leida.getEventos()).hasSize(1);
    }

    @Test
    void encuentraLasAprobadasAnterioresAlLimite() {
        Solicitud s = pendiente(1L, "ana");
        s.aprobar(null, AHORA.minusDays(10), "coord");
        repository.saveAndFlush(s);

        assertThat(repository.findByEstadoAndFechaAprobacionBefore(EstadoSolicitud.APROBADA, AHORA.minusDays(7)))
                .extracting(Solicitud::getId).containsExactly(s.getId());
        assertThat(repository.findByEstadoAndFechaAprobacionBefore(EstadoSolicitud.APROBADA, AHORA.minusDays(11)))
                .isEmpty();
    }
}

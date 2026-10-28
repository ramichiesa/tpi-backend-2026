package utnfc.isi.back.tpi.adopciones.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utnfc.isi.back.tpi.adopciones.model.EstadoSolicitud;
import utnfc.isi.back.tpi.adopciones.model.Solicitud;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long> {

    /** R-02: el adoptante ya tiene una solicitud activa. */
    boolean existsByAdoptanteIdAndEstadoIn(String adoptanteId, Collection<EstadoSolicitud> estados);

    /** R-01: el animal ya tiene una solicitud PENDIENTE. */
    boolean existsByAnimalIdAndEstado(Long animalId, EstadoSolicitud estado);

    List<Solicitud> findByAdoptanteIdOrderByIdDesc(String adoptanteId);

    List<Solicitud> findByAdoptanteIdAndEstadoOrderByIdDesc(String adoptanteId, EstadoSolicitud estado);

    List<Solicitud> findAllByOrderByIdDesc();

    List<Solicitud> findByEstadoOrderByIdDesc(EstadoSolicitud estado);

    /** Candidatas a vencimiento de plazo de entrega (R-09), para la tarea programada. */
    List<Solicitud> findByEstadoAndFechaAprobacionBefore(EstadoSolicitud estado, LocalDateTime limite);
}

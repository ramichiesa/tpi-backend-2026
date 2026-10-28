package utnfc.isi.back.tpi.adopciones.dto;

import utnfc.isi.back.tpi.adopciones.model.EstadoSolicitud;
import utnfc.isi.back.tpi.adopciones.model.ResultadoControl;
import utnfc.isi.back.tpi.adopciones.model.Solicitud;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Representacion de una solicitud (CONTRATOS.md, seccion 7) con el resultado de las
 * verificaciones automaticas. No incluye la direccion del adoptante.
 * Agrega a lo acordado animalNombre, observacionesControl y motivoCierre (trazabilidad).
 */
public record SolicitudResponse(
        Long id,
        Long animalId,
        String animalNombre,
        String adoptanteId,
        EstadoSolicitud estado,
        LocalDateTime fechaCreacion,
        Double distanciaKm,
        boolean requiereExcepcion,
        boolean distanciaNoVerificada,
        List<String> observaciones,
        String motivoRechazo,
        String justificacionExcepcion,
        LocalDateTime fechaAprobacion,
        LocalDateTime fechaEntrega,
        LocalDateTime fechaControl,
        ResultadoControl resultadoControl,
        String observacionesControl,
        String motivoCierre) {

    public static SolicitudResponse desde(Solicitud s) {
        return new SolicitudResponse(s.getId(), s.getAnimalId(), s.getAnimalNombre(), s.getAdoptanteId(),
                s.getEstado(), s.getFechaCreacion(), s.getDistanciaKm(), s.isRequiereExcepcion(),
                s.isDistanciaNoVerificada(), List.copyOf(s.getObservaciones()), s.getMotivoRechazo(),
                s.getJustificacionExcepcion(), s.getFechaAprobacion(), s.getFechaEntrega(), s.getFechaControl(),
                s.getResultadoControl(), s.getObservacionesControl(), s.getMotivoCierre());
    }
}

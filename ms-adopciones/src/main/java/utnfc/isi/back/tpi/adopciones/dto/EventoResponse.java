package utnfc.isi.back.tpi.adopciones.dto;

import utnfc.isi.back.tpi.adopciones.model.EstadoSolicitud;
import utnfc.isi.back.tpi.adopciones.model.EventoSolicitud;

import java.time.LocalDateTime;

/** Una entrada del historial de la solicitud. */
public record EventoResponse(
        LocalDateTime fecha,
        EstadoSolicitud estadoAnterior,
        EstadoSolicitud estadoNuevo,
        String actor,
        String detalle) {

    public static EventoResponse desde(EventoSolicitud e) {
        return new EventoResponse(e.getFecha(), e.getEstadoAnterior(), e.getEstadoNuevo(), e.getActor(), e.getDetalle());
    }
}

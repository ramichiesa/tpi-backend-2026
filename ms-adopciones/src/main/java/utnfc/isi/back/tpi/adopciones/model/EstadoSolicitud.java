package utnfc.isi.back.tpi.adopciones.model;

/**
 * Estados de una solicitud de adopcion (documento de dominio, seccion 3.6).
 *
 * PENDIENTE -> APROBADA -> EN_ADAPTACION -> FINALIZADA
 *          \-> RECHAZADA        \-> CANCELADA (entrega cancelada o plazo vencido)
 *          \-> CANCELADA         EN_ADAPTACION -> REVERTIDA (control desfavorable o devolucion)
 */
public enum EstadoSolicitud {
    PENDIENTE,
    APROBADA,
    RECHAZADA,
    CANCELADA,
    EN_ADAPTACION,
    FINALIZADA,
    REVERTIDA;

    /** R-02: una persona puede tener a lo sumo una solicitud activa a la vez. */
    public boolean esActiva() {
        return this == PENDIENTE || this == APROBADA || this == EN_ADAPTACION;
    }
}

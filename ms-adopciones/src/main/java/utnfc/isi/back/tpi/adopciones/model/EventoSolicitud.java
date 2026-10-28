package utnfc.isi.back.tpi.adopciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Historial de la solicitud: cada cambio de estado queda registrado con su fecha, quien lo
 * hizo (claim "sub" o "sistema" para la tarea programada) y el motivo. Es la trazabilidad
 * de las decisiones humanas que pide el dominio (R-08). Nunca se modifica ni se borra.
 */
@Entity
@Table(name = "evento_solicitud")
public class EventoSolicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "solicitud_id", nullable = false)
    private Solicitud solicitud;

    @Column(nullable = false)
    private LocalDateTime fecha;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_anterior", length = 20)
    private EstadoSolicitud estadoAnterior;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private EstadoSolicitud estadoNuevo;

    @Column(nullable = false, length = 64)
    private String actor;

    @Column(length = 1000)
    private String detalle;

    protected EventoSolicitud() {
        // requerido por JPA
    }

    EventoSolicitud(Solicitud solicitud, LocalDateTime fecha, EstadoSolicitud estadoAnterior,
                    EstadoSolicitud estadoNuevo, String actor, String detalle) {
        this.solicitud = solicitud;
        this.fecha = fecha;
        this.estadoAnterior = estadoAnterior;
        this.estadoNuevo = estadoNuevo;
        this.actor = actor;
        this.detalle = detalle;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public EstadoSolicitud getEstadoAnterior() {
        return estadoAnterior;
    }

    public EstadoSolicitud getEstadoNuevo() {
        return estadoNuevo;
    }

    public String getActor() {
        return actor;
    }

    public String getDetalle() {
        return detalle;
    }
}

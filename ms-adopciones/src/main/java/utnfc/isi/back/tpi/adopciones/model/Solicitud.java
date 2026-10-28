package utnfc.isi.back.tpi.adopciones.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import utnfc.isi.back.tpi.common.exception.ConflictoException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Solicitud de adopcion: agregado central del servicio. Todas las transiciones de estado pasan
 * por metodos de esta clase, que verifican el estado de origen, registran el evento en el
 * historial y recalculan las columnas de bloqueo.
 *
 * Columnas de bloqueo (defensa ante concurrencia, ultima linea de R-01 y R-02):
 * - animal_pendiente = animalId mientras la solicitud este PENDIENTE; null en otro caso.
 * - adoptante_activo = adoptanteId mientras este activa (PENDIENTE, APROBADA, EN_ADAPTACION).
 * Ambas tienen restriccion UNIQUE. Como la base admite varios null, solo puede existir una
 * solicitud PENDIENTE por animal y una activa por adoptante, aun si dos peticiones llegan a la
 * vez y las dos pasan la verificacion previa en memoria. Se eligio esta tecnica en lugar de un
 * indice parcial de PostgreSQL porque funciona con ddl-auto (contrato) y con H2 en las pruebas.
 */
@Entity
@Table(name = "solicitud", uniqueConstraints = {
        @UniqueConstraint(name = "uk_solicitud_animal_pendiente", columnNames = "animal_pendiente"),
        @UniqueConstraint(name = "uk_solicitud_adoptante_activo", columnNames = "adoptante_activo")
})
public class Solicitud {

    public static final String ACTOR_SISTEMA = "sistema";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "animal_id", nullable = false)
    private Long animalId;

    @Column(name = "animal_nombre", length = 100)
    private String animalNombre;

    @Column(name = "adoptante_id", nullable = false, length = 64)
    private String adoptanteId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoSolicitud estado;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    // --- Resultado de la evaluacion automatica (R-04 a R-06) ---
    @Column(name = "distancia_km")
    private Double distanciaKm;

    @Column(name = "requiere_excepcion", nullable = false)
    private boolean requiereExcepcion;

    @Column(name = "distancia_no_verificada", nullable = false)
    private boolean distanciaNoVerificada;

    @ElementCollection
    @CollectionTable(name = "solicitud_observacion", joinColumns = @JoinColumn(name = "solicitud_id"))
    @OrderColumn(name = "orden")
    @Column(name = "texto", length = 500, nullable = false)
    private List<String> observaciones = new ArrayList<>();

    // --- Decision del Coordinador (R-07, R-08) ---
    @Column(name = "motivo_rechazo", length = 1000)
    private String motivoRechazo;

    @Column(name = "justificacion_excepcion", length = 1000)
    private String justificacionExcepcion;

    @Column(name = "fecha_aprobacion")
    private LocalDateTime fechaAprobacion;

    // --- Entrega y seguimiento (R-09 a R-12) ---
    @Column(name = "fecha_entrega")
    private LocalDateTime fechaEntrega;

    @Column(name = "fecha_control")
    private LocalDateTime fechaControl;

    @Enumerated(EnumType.STRING)
    @Column(name = "resultado_control", length = 20)
    private ResultadoControl resultadoControl;

    @Column(name = "observaciones_control", length = 1000)
    private String observacionesControl;

    /** Motivo de cancelacion, cancelacion de entrega, vencimiento o devolucion. */
    @Column(name = "motivo_cierre", length = 1000)
    private String motivoCierre;

    // --- Columnas de bloqueo (ver Javadoc de la clase) ---
    @Column(name = "animal_pendiente")
    private Long animalPendiente;

    @Column(name = "adoptante_activo", length = 64)
    private String adoptanteActivo;

    @OneToMany(mappedBy = "solicitud", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("fecha ASC, id ASC")
    private List<EventoSolicitud> eventos = new ArrayList<>();

    @Version
    private long version;

    protected Solicitud() {
        // requerido por JPA
    }

    private Solicitud(Long animalId, String animalNombre, String adoptanteId, LocalDateTime ahora) {
        this.animalId = animalId;
        this.animalNombre = animalNombre;
        this.adoptanteId = adoptanteId;
        this.fechaCreacion = ahora;
    }

    /** Solicitud que supero R-01 a R-04: queda PENDIENTE con las marcas de la evaluacion. */
    public static Solicitud pendiente(Long animalId, String animalNombre, String adoptanteId, LocalDateTime ahora,
                                      Double distanciaKm, boolean requiereExcepcion, boolean distanciaNoVerificada,
                                      List<String> observaciones) {
        Solicitud s = new Solicitud(animalId, animalNombre, adoptanteId, ahora);
        s.distanciaKm = distanciaKm;
        s.requiereExcepcion = requiereExcepcion;
        s.distanciaNoVerificada = distanciaNoVerificada;
        s.observaciones.addAll(observaciones);
        s.cambiarEstado(EstadoSolicitud.PENDIENTE, ahora, adoptanteId, "Solicitud presentada");
        return s;
    }

    /** R-04: incompatibilidad dura. La solicitud se registra RECHAZADA con el motivo, para dejar traza. */
    public static Solicitud rechazadaPorIncompatibilidad(Long animalId, String animalNombre, String adoptanteId,
                                                        LocalDateTime ahora, String motivo) {
        Solicitud s = new Solicitud(animalId, animalNombre, adoptanteId, ahora);
        s.motivoRechazo = motivo;
        s.cambiarEstado(EstadoSolicitud.RECHAZADA, ahora, ACTOR_SISTEMA, "Rechazo automatico (R-04): " + motivo);
        return s;
    }

    /** R-07 y R-08: verifica, sin modificar nada, que la solicitud pueda aprobarse. */
    public void verificarAprobable(String justificacion) {
        exigirEstado(EstadoSolicitud.PENDIENTE, "aprobar");
        if (requiereExcepcion && (justificacion == null || justificacion.isBlank())) {
            throw new ConflictoException("La solicitud " + id + " supera la distancia maxima: aprobarla requiere "
                    + "una justificacion escrita (R-08)");
        }
    }

    public void aprobar(String justificacion, LocalDateTime ahora, String actor) {
        verificarAprobable(justificacion);
        this.justificacionExcepcion = (justificacion == null || justificacion.isBlank()) ? null : justificacion;
        this.fechaAprobacion = ahora;
        cambiarEstado(EstadoSolicitud.APROBADA, ahora, actor, justificacionExcepcion);
    }

    /** R-08: todo rechazo requiere motivo (la validacion de que no este vacio la hace el DTO). */
    public void rechazar(String motivo, LocalDateTime ahora, String actor) {
        exigirEstado(EstadoSolicitud.PENDIENTE, "rechazar");
        this.motivoRechazo = motivo;
        cambiarEstado(EstadoSolicitud.RECHAZADA, ahora, actor, motivo);
    }

    /** El adoptante desiste mientras la solicitud esta pendiente. */
    public void cancelarPorAdoptante(LocalDateTime ahora, String actor) {
        exigirEstado(EstadoSolicitud.PENDIENTE, "cancelar");
        this.motivoCierre = "Cancelada por el adoptante";
        cambiarEstado(EstadoSolicitud.CANCELADA, ahora, actor, motivoCierre);
    }

    /** R-09: indica si ya paso el plazo maximo para confirmar la entrega desde la aprobacion. */
    public boolean plazoEntregaVencido(LocalDateTime ahora, int plazoEntregaDias) {
        return estado == EstadoSolicitud.APROBADA && fechaAprobacion != null
                && ahora.isAfter(fechaAprobacion.plusDays(plazoEntregaDias));
    }

    /** R-10: confirma la entrega y calcula la fecha del control final. */
    public void confirmarEntrega(LocalDateTime ahora, int plazoEntregaDias, int periodoAdaptacionDias, String actor) {
        exigirEstado(EstadoSolicitud.APROBADA, "confirmar la entrega de");
        if (plazoEntregaVencido(ahora, plazoEntregaDias)) {
            throw new ConflictoException("El plazo de entrega de la solicitud " + id + " vencio (R-09)");
        }
        this.fechaEntrega = ahora;
        this.fechaControl = ahora.plusDays(periodoAdaptacionDias);
        cambiarEstado(EstadoSolicitud.EN_ADAPTACION, ahora, actor, "Entrega confirmada; control final desde " + fechaControl);
    }

    /** P3: la entrega no se concreta (el adoptante desiste u otra causa). */
    public void cancelarEntrega(String motivo, LocalDateTime ahora, String actor) {
        exigirEstado(EstadoSolicitud.APROBADA, "cancelar la entrega de");
        this.motivoCierre = motivo;
        cambiarEstado(EstadoSolicitud.CANCELADA, ahora, actor, "Entrega cancelada: " + motivo);
    }

    /** R-09: vencio el plazo sin entrega; lo aplica la tarea programada o el intento de confirmar. */
    public void vencerPlazoEntrega(LocalDateTime ahora, int plazoEntregaDias) {
        if (!plazoEntregaVencido(ahora, plazoEntregaDias)) {
            throw new ConflictoException("El plazo de entrega de la solicitud " + id + " no esta vencido");
        }
        this.motivoCierre = "Plazo de entrega de " + plazoEntregaDias + " dias vencido (R-09)";
        cambiarEstado(EstadoSolicitud.CANCELADA, ahora, ACTOR_SISTEMA, motivoCierre);
    }

    /** R-11 (conforme) y R-12 (desfavorable). Solo desde la fecha de control. */
    public void registrarControl(ResultadoControl resultado, String observacionesControl, LocalDateTime ahora, String actor) {
        exigirEstado(EstadoSolicitud.EN_ADAPTACION, "registrar el control final de");
        if (ahora.isBefore(fechaControl)) {
            throw new ConflictoException("El control final de la solicitud " + id + " recien puede registrarse desde "
                    + fechaControl + " (R-11)");
        }
        this.resultadoControl = resultado;
        this.observacionesControl = observacionesControl;
        if (resultado == ResultadoControl.CONFORME) {
            cambiarEstado(EstadoSolicitud.FINALIZADA, ahora, actor, "Control final conforme");
        } else {
            this.motivoCierre = observacionesControl;
            cambiarEstado(EstadoSolicitud.REVERTIDA, ahora, actor, "Control final desfavorable: " + observacionesControl);
        }
    }

    /** R-12: el adoptante devuelve al animal durante el periodo de adaptacion. */
    public void devolver(String motivo, LocalDateTime ahora, String actor) {
        exigirEstado(EstadoSolicitud.EN_ADAPTACION, "pedir la devolucion de");
        this.motivoCierre = motivo;
        cambiarEstado(EstadoSolicitud.REVERTIDA, ahora, actor, "Devolucion: " + motivo);
    }

    public boolean perteneceA(String usuario) {
        return adoptanteId.equals(usuario);
    }

    private void exigirEstado(EstadoSolicitud esperado, String accion) {
        if (estado != esperado) {
            throw new ConflictoException("No se puede " + accion + " la solicitud " + id + ": esta " + estado
                    + " y debe estar " + esperado);
        }
    }

    private void cambiarEstado(EstadoSolicitud nuevo, LocalDateTime ahora, String actor, String detalle) {
        EstadoSolicitud anterior = this.estado;
        this.estado = nuevo;
        this.animalPendiente = nuevo == EstadoSolicitud.PENDIENTE ? animalId : null;
        this.adoptanteActivo = nuevo.esActiva() ? adoptanteId : null;
        this.eventos.add(new EventoSolicitud(this, ahora, anterior, nuevo, actor, detalle));
    }

    public Long getId() {
        return id;
    }

    public Long getAnimalId() {
        return animalId;
    }

    public String getAnimalNombre() {
        return animalNombre;
    }

    public String getAdoptanteId() {
        return adoptanteId;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public Double getDistanciaKm() {
        return distanciaKm;
    }

    public boolean isRequiereExcepcion() {
        return requiereExcepcion;
    }

    public boolean isDistanciaNoVerificada() {
        return distanciaNoVerificada;
    }

    public List<String> getObservaciones() {
        return Collections.unmodifiableList(observaciones);
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public String getJustificacionExcepcion() {
        return justificacionExcepcion;
    }

    public LocalDateTime getFechaAprobacion() {
        return fechaAprobacion;
    }

    public LocalDateTime getFechaEntrega() {
        return fechaEntrega;
    }

    public LocalDateTime getFechaControl() {
        return fechaControl;
    }

    public ResultadoControl getResultadoControl() {
        return resultadoControl;
    }

    public String getObservacionesControl() {
        return observacionesControl;
    }

    public String getMotivoCierre() {
        return motivoCierre;
    }

    public List<EventoSolicitud> getEventos() {
        return Collections.unmodifiableList(eventos);
    }

    public long getVersion() {
        return version;
    }
}

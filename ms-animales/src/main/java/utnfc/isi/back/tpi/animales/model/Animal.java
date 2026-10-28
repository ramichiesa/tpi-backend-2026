package utnfc.isi.back.tpi.animales.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import utnfc.isi.back.tpi.common.exception.ConflictoException;

import java.time.LocalDateTime;

/**
 * Animal del refugio. Es el duenio de su propio estado: cualquier cambio pasa por los
 * metodos de esta clase, que rechazan las transiciones invalidas.
 *
 * {@code @Version} activa el bloqueo optimista: si dos operaciones intentan cambiar el
 * mismo animal a la vez, la segunda falla y se responde 409 en lugar de pisar a la primera.
 */
@Entity
@Table(name = "animal")
public class Animal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Especie especie;

    @Column(name = "edad_meses", nullable = false)
    private int edadMeses;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Tamanio tamanio;

    @Column(name = "necesidades_especiales", length = 500)
    private String necesidadesEspeciales;

    @Column(name = "apto_ninios", nullable = false)
    private boolean aptoNinios;

    @Column(name = "apto_otros_animales", nullable = false)
    private boolean aptoOtrosAnimales;

    @Column(name = "requiere_espacio_exterior", nullable = false)
    private boolean requiereEspacioExterior;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoAnimal estado;

    @Column(name = "motivo_no_disponible", length = 500)
    private String motivoNoDisponible;

    @Column(name = "fecha_alta", nullable = false)
    private LocalDateTime fechaAlta;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    @Version
    private long version;

    protected Animal() {
        // requerido por JPA
    }

    /** Alta de un animal: siempre empieza DISPONIBLE (P1). */
    public Animal(String nombre, Especie especie, int edadMeses, Tamanio tamanio, String necesidadesEspeciales,
                  boolean aptoNinios, boolean aptoOtrosAnimales, boolean requiereEspacioExterior) {
        actualizarDatos(nombre, especie, edadMeses, tamanio, necesidadesEspeciales,
                aptoNinios, aptoOtrosAnimales, requiereEspacioExterior);
        this.estado = EstadoAnimal.DISPONIBLE;
        this.fechaAlta = LocalDateTime.now();
        this.fechaActualizacion = this.fechaAlta;
    }

    /** Modifica los datos descriptivos. No toca el estado: para eso estan los metodos de abajo. */
    public void actualizarDatos(String nombre, Especie especie, int edadMeses, Tamanio tamanio,
                                String necesidadesEspeciales, boolean aptoNinios, boolean aptoOtrosAnimales,
                                boolean requiereEspacioExterior) {
        this.nombre = nombre;
        this.especie = especie;
        this.edadMeses = edadMeses;
        this.tamanio = tamanio;
        this.necesidadesEspeciales = necesidadesEspeciales;
        this.aptoNinios = aptoNinios;
        this.aptoOtrosAnimales = aptoOtrosAnimales;
        this.requiereEspacioExterior = requiereEspacioExterior;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /**
     * Cambio de estado pedido por el proceso de adopcion (API interna).
     * Solo se aceptan las transiciones de {@link EstadoAnimal#admiteTransicionDelProceso}.
     */
    public void cambiarEstadoPorProceso(EstadoAnimal destino) {
        if (!estado.admiteTransicionDelProceso(destino)) {
            throw new ConflictoException("Transicion invalida para el animal " + id + ": "
                    + estado + " -> " + destino);
        }
        this.estado = destino;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /**
     * R-13: un animal con una adopcion en curso (RESERVADO o EN_ADAPTACION) no puede quedar
     * NO_DISPONIBLE sin resolver antes ese proceso. Un animal ADOPTADO ya salio del refugio.
     */
    public void marcarNoDisponible(String motivo) {
        if (estado.tieneAdopcionEnCurso()) {
            throw new ConflictoException("El animal " + id + " tiene una adopcion en curso (" + estado
                    + "); primero hay que resolverla (R-13)");
        }
        if (estado != EstadoAnimal.DISPONIBLE) {
            throw new ConflictoException("Solo un animal DISPONIBLE puede pasar a NO_DISPONIBLE; estado actual: " + estado);
        }
        this.estado = EstadoAnimal.NO_DISPONIBLE;
        this.motivoNoDisponible = motivo;
        this.fechaActualizacion = LocalDateTime.now();
    }

    /** Vuelve a publicar un animal NO_DISPONIBLE cuando se resolvio la causa. */
    public void marcarDisponible() {
        if (estado != EstadoAnimal.NO_DISPONIBLE) {
            throw new ConflictoException("Solo un animal NO_DISPONIBLE puede volver a DISPONIBLE por esta via; estado actual: " + estado);
        }
        this.estado = EstadoAnimal.DISPONIBLE;
        this.motivoNoDisponible = null;
        this.fechaActualizacion = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public Especie getEspecie() {
        return especie;
    }

    public int getEdadMeses() {
        return edadMeses;
    }

    public Tamanio getTamanio() {
        return tamanio;
    }

    public String getNecesidadesEspeciales() {
        return necesidadesEspeciales;
    }

    public boolean isAptoNinios() {
        return aptoNinios;
    }

    public boolean isAptoOtrosAnimales() {
        return aptoOtrosAnimales;
    }

    public boolean isRequiereEspacioExterior() {
        return requiereEspacioExterior;
    }

    public EstadoAnimal getEstado() {
        return estado;
    }

    public String getMotivoNoDisponible() {
        return motivoNoDisponible;
    }

    public LocalDateTime getFechaAlta() {
        return fechaAlta;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public long getVersion() {
        return version;
    }
}

package utnfc.isi.back.tpi.adopciones.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Perfil del adoptante: datos de contacto, domicilio y condiciones del hogar (autodeclarados).
 * La clave es el claim "sub" del JWT, de modo que cada usuario tiene un unico perfil y
 * no hace falta una tabla de usuarios propia (la identidad la administra Keycloak).
 *
 * Todos los campos son obligatorios al guardarlo, por eso un perfil existente esta completo (R-03).
 */
@Entity
@Table(name = "perfil")
public class Perfil {

    @Id
    @Column(name = "adoptante_id", length = 64)
    private String adoptanteId;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(nullable = false, length = 40)
    private String telefono;

    @Column(nullable = false, length = 100)
    private String provincia;

    @Column(nullable = false, length = 100)
    private String localidad;

    @Column(nullable = false, length = 200)
    private String direccion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_vivienda", nullable = false, length = 20)
    private TipoVivienda tipoVivienda;

    @Column(name = "tiene_patio", nullable = false)
    private boolean tienePatio;

    @Column(name = "hay_ninios", nullable = false)
    private boolean hayNinios;

    @Column(name = "hay_otros_animales", nullable = false)
    private boolean hayOtrosAnimales;

    @Column(name = "fecha_actualizacion", nullable = false)
    private LocalDateTime fechaActualizacion;

    protected Perfil() {
        // requerido por JPA
    }

    public Perfil(String adoptanteId) {
        this.adoptanteId = adoptanteId;
    }

    public void actualizar(String nombreCompleto, String telefono, String provincia, String localidad,
                           String direccion, TipoVivienda tipoVivienda, boolean tienePatio, boolean hayNinios,
                           boolean hayOtrosAnimales, LocalDateTime ahora) {
        this.nombreCompleto = nombreCompleto;
        this.telefono = telefono;
        this.provincia = provincia;
        this.localidad = localidad;
        this.direccion = direccion;
        this.tipoVivienda = tipoVivienda;
        this.tienePatio = tienePatio;
        this.hayNinios = hayNinios;
        this.hayOtrosAnimales = hayOtrosAnimales;
        this.fechaActualizacion = ahora;
    }

    public String getAdoptanteId() {
        return adoptanteId;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getTelefono() {
        return telefono;
    }

    public String getProvincia() {
        return provincia;
    }

    public String getLocalidad() {
        return localidad;
    }

    public String getDireccion() {
        return direccion;
    }

    public TipoVivienda getTipoVivienda() {
        return tipoVivienda;
    }

    public boolean isTienePatio() {
        return tienePatio;
    }

    public boolean isHayNinios() {
        return hayNinios;
    }

    public boolean isHayOtrosAnimales() {
        return hayOtrosAnimales;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}

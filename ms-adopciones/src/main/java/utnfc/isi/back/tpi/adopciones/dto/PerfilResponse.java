package utnfc.isi.back.tpi.adopciones.dto;

import utnfc.isi.back.tpi.adopciones.model.Perfil;
import utnfc.isi.back.tpi.adopciones.model.TipoVivienda;

import java.time.LocalDateTime;

/**
 * Perfil completo, con la direccion exacta. Solo lo ven el propio adoptante y el Coordinador
 * (supuesto de privacidad del documento de dominio, seccion 3.5).
 */
public record PerfilResponse(
        String adoptanteId,
        String nombreCompleto,
        String telefono,
        String provincia,
        String localidad,
        String direccion,
        TipoVivienda tipoVivienda,
        boolean tienePatio,
        boolean hayNinios,
        boolean hayOtrosAnimales,
        LocalDateTime fechaActualizacion) {

    public static PerfilResponse desde(Perfil p) {
        return new PerfilResponse(p.getAdoptanteId(), p.getNombreCompleto(), p.getTelefono(), p.getProvincia(),
                p.getLocalidad(), p.getDireccion(), p.getTipoVivienda(), p.isTienePatio(), p.isHayNinios(),
                p.isHayOtrosAnimales(), p.getFechaActualizacion());
    }
}

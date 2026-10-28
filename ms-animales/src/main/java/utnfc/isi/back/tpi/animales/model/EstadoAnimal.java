package utnfc.isi.back.tpi.animales.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Estados del animal y transiciones permitidas (CONTRATOS.md, seccion 6.3).
 *
 * Hay dos tipos de cambio de estado:
 * - Los del proceso de adopcion, que solo puede pedir el servicio de Adopciones por la API interna.
 * - Los administrativos (NO_DISPONIBLE y su reversion), que hace el Coordinador por la API publica
 *   y estan en {@link Animal#marcarNoDisponible(String)} y {@link Animal#marcarDisponible()}.
 */
public enum EstadoAnimal {
    DISPONIBLE,
    RESERVADO,
    EN_ADAPTACION,
    ADOPTADO,
    NO_DISPONIBLE;

    private static final Map<EstadoAnimal, Set<EstadoAnimal>> TRANSICIONES_DEL_PROCESO = Map.of(
            DISPONIBLE, EnumSet.of(RESERVADO),                 // se aprueba una solicitud
            RESERVADO, EnumSet.of(EN_ADAPTACION, DISPONIBLE),  // entrega confirmada / cancelada o vencida
            EN_ADAPTACION, EnumSet.of(ADOPTADO, DISPONIBLE)    // control conforme / desfavorable o devolucion
    );

    /** Indica si el proceso de adopcion puede llevar al animal desde este estado al destino. */
    public boolean admiteTransicionDelProceso(EstadoAnimal destino) {
        return TRANSICIONES_DEL_PROCESO.getOrDefault(this, Set.of()).contains(destino);
    }

    /** RESERVADO o EN_ADAPTACION: hay una adopcion en curso que no puede quedar colgada (R-13). */
    public boolean tieneAdopcionEnCurso() {
        return this == RESERVADO || this == EN_ADAPTACION;
    }
}

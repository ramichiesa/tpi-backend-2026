package utnfc.isi.back.tpi.common.security;

/**
 * Nombres de los roles de realm definidos en Keycloak (CONTRATOS.md, seccion 3.1).
 * Se comparten para que los tres servicios usen exactamente los mismos textos.
 */
public final class Roles {

    public static final String ADOPTANTE = "ADOPTANTE";
    public static final String COORDINADOR = "COORDINADOR";
    public static final String SERVICIO = "SERVICIO";

    private Roles() {
    }
}

package utnfc.isi.back.tpi.common.exception;

/**
 * Otro servicio o un proveedor externo no responde (503).
 */
public class ServicioNoDisponibleException extends RuntimeException {

    public ServicioNoDisponibleException(String message) {
        super(message);
    }

    public ServicioNoDisponibleException(String message, Throwable cause) {
        super(message, cause);
    }
}

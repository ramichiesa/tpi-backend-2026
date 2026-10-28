package utnfc.isi.back.tpi.common.exception;

/**
 * La operacion viola una regla de negocio o el estado actual lo impide (409).
 */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String message) {
        super(message);
    }

    public ConflictoException(String message, Throwable cause) {
        super(message, cause);
    }
}

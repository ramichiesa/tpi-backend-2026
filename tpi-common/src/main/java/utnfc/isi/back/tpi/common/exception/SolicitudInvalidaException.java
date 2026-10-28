package utnfc.isi.back.tpi.common.exception;

/**
 * Los datos recibidos no son validos (400).
 */
public class SolicitudInvalidaException extends RuntimeException {

    public SolicitudInvalidaException(String message) {
        super(message);
    }

    public SolicitudInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}

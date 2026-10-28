package utnfc.isi.back.tpi.common.exception;

/**
 * El usuario esta autenticado pero no puede operar sobre ese recurso, por ejemplo
 * porque pertenece a otra persona (403).
 */
public class AccesoDenegadoException extends RuntimeException {

    public AccesoDenegadoException(String message) {
        super(message);
    }
}

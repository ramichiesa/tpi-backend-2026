package utnfc.isi.back.tpi.common.error;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Formato unico de error de todos los servicios (ver CONTRATOS.md, seccion 4).
 */
public record ErrorResponse(
        LocalDateTime timestamp,
        int status,
        String error,
        String message,
        String path,
        List<CampoError> errores) {

    /** Detalle de un campo invalido (solo se completa en los 400 de validacion). */
    public record CampoError(String campo, String mensaje) {
    }

    public static ErrorResponse of(HttpStatus status, String message, String path, List<CampoError> errores) {
        return new ErrorResponse(LocalDateTime.now(), status.value(), descripcion(status), message, path, errores);
    }

    /** Texto en castellano del campo "error", como en el ejemplo del contrato ("Conflicto"). */
    static String descripcion(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "Solicitud invalida";
            case UNAUTHORIZED -> "No autenticado";
            case FORBIDDEN -> "Prohibido";
            case NOT_FOUND -> "No encontrado";
            case METHOD_NOT_ALLOWED -> "Metodo no permitido";
            case CONFLICT -> "Conflicto";
            case UNSUPPORTED_MEDIA_TYPE -> "Tipo de contenido no soportado";
            case SERVICE_UNAVAILABLE -> "Servicio no disponible";
            case INTERNAL_SERVER_ERROR -> "Error interno";
            default -> status.getReasonPhrase();
        };
    }
}

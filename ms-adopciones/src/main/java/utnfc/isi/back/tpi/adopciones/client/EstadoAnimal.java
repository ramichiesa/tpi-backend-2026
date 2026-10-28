package utnfc.isi.back.tpi.adopciones.client;

/**
 * Copia local de los estados que publica el contrato de ms-animales.
 * No se comparte por el JAR comun a proposito: es parte del contrato HTTP entre dos
 * servicios y no un modelo de dominio compartido (cada servicio conserva su modelo).
 */
public enum EstadoAnimal {
    DISPONIBLE,
    RESERVADO,
    EN_ADAPTACION,
    ADOPTADO,
    NO_DISPONIBLE
}

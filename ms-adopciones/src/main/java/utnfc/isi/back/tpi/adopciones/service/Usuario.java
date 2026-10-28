package utnfc.isi.back.tpi.adopciones.service;

/**
 * Quien realiza la operacion: el claim "sub" del JWT y si tiene el rol COORDINADOR.
 * Lo arma el controlador a partir del token, para que el servicio no dependa de Spring Security.
 */
public record Usuario(String id, boolean coordinador) {
}

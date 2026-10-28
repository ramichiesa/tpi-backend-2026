package utnfc.isi.back.tpi.adopciones.service;

import utnfc.isi.back.tpi.adopciones.client.Coordenadas;

/**
 * Distancia aproximada en linea recta entre dos puntos (formula de Haversine).
 * Georef solo devuelve coordenadas: la conclusion "esta lejos" la saca nuestro servicio.
 * Limitacion conocida: no es la distancia por ruta (documento de dominio, seccion 8.5).
 */
public final class CalculadoraDistancia {

    private static final double RADIO_TIERRA_KM = 6371.0;

    private CalculadoraDistancia() {
    }

    /** Distancia en km, redondeada a un decimal. */
    public static double km(Coordenadas a, Coordenadas b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double km = 2 * RADIO_TIERRA_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
        return Math.round(km * 10.0) / 10.0;
    }
}

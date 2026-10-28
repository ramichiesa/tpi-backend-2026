package utnfc.isi.back.tpi.adopciones.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tarea programada que vigila los plazos de entrega vencidos (R-09): una solicitud APROBADA
 * que no se entrega dentro del plazo se cancela y el animal vuelve a DISPONIBLE, para que no
 * quede reservado indefinidamente.
 *
 * Vive dentro del servicio (no es un contenedor aparte). Usa el token de servicio para hablar
 * con Animales porque no hay ningun usuario en esta ejecucion. Si Animales no responde, la
 * solicitud queda como estaba y se reintenta en la proxima ejecucion.
 */
@Component
@ConditionalOnProperty(name = "adopciones.tareas.habilitadas", havingValue = "true", matchIfMissing = true)
public class TareaVencimientos {

    private static final Logger log = LoggerFactory.getLogger(TareaVencimientos.class);

    private final SolicitudService solicitudService;

    public TareaVencimientos(SolicitudService solicitudService) {
        this.solicitudService = solicitudService;
    }

    @Scheduled(initialDelayString = "${adopciones.tareas.demora-inicial-ms:60000}",
            fixedDelayString = "${adopciones.tareas.intervalo-ms:300000}")
    public void vencerEntregas() {
        List<Long> candidatas = solicitudService.buscarEntregasVencidas();
        int vencidas = 0;
        for (Long id : candidatas) {
            try {
                if (solicitudService.vencerEntrega(id)) {
                    vencidas++;
                }
            } catch (RuntimeException e) {
                log.warn("No se pudo vencer la solicitud {} ({}); se reintenta en la proxima ejecucion", id, e.getMessage());
            }
        }
        if (!candidatas.isEmpty()) {
            log.info("Vencimientos de entrega: {} candidatas, {} canceladas", candidatas.size(), vencidas);
        }
    }
}

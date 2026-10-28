package utnfc.isi.back.tpi.adopciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Se escanea tambien el paquete de la libreria comun para tomar su manejador de errores,
 * el filtro de correlacion y los manejadores de 401/403.
 * EnableScheduling activa la tarea que vigila los plazos de entrega vencidos (R-09).
 */
@SpringBootApplication(scanBasePackages = {"utnfc.isi.back.tpi.adopciones", "utnfc.isi.back.tpi.common"})
@ConfigurationPropertiesScan
@EnableScheduling
public class AdopcionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdopcionesApplication.class, args);
    }
}

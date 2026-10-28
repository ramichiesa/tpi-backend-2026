package utnfc.isi.back.tpi.donaciones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Se escanea tambien el paquete de la libreria comun para tomar su manejador de errores,
 * el filtro de correlacion y los manejadores de 401/403.
 */
@SpringBootApplication(scanBasePackages = {"utnfc.isi.back.tpi.donaciones", "utnfc.isi.back.tpi.common"})
@ConfigurationPropertiesScan
public class DonacionesApplication {

    public static void main(String[] args) {
        SpringApplication.run(DonacionesApplication.class, args);
    }
}

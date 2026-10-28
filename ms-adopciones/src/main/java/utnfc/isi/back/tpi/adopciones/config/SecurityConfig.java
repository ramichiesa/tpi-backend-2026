package utnfc.isi.back.tpi.adopciones.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import utnfc.isi.back.tpi.common.security.JsonAccessDeniedHandler;
import utnfc.isi.back.tpi.common.security.JsonAuthenticationEntryPoint;
import utnfc.isi.back.tpi.common.security.ResourceServerDefaults;

import static utnfc.isi.back.tpi.common.security.Roles.ADOPTANTE;
import static utnfc.isi.back.tpi.common.security.Roles.COORDINADOR;

/**
 * Matriz de permisos de ms-adopciones (CONTRATOS.md, seccion 3.5).
 * Los roles resuelven "que tipo de usuario puede hacer esto"; la pertenencia
 * ("esta solicitud es tuya") la verifica SolicitudService y responde 403.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JsonAuthenticationEntryPoint entryPoint,
                                            JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        ResourceServerDefaults.aplicar(http, entryPoint, accessDeniedHandler)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Perfil: el propio (ADOPTANTE) y el de cualquier postulante (COORDINADOR)
                        .requestMatchers("/api/adoptantes/me/perfil").hasRole(ADOPTANTE)
                        .requestMatchers(HttpMethod.GET, "/api/adoptantes/*/perfil").hasRole(COORDINADOR)
                        // Acciones del adoptante
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes").hasRole(ADOPTANTE)
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes/*/cancelar", "/api/solicitudes/*/devolucion")
                        .hasRole(ADOPTANTE)
                        // Decisiones del Coordinador
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes/*/aprobar", "/api/solicitudes/*/rechazar",
                                "/api/solicitudes/*/confirmar-entrega", "/api/solicitudes/*/cancelar-entrega",
                                "/api/solicitudes/*/control-final")
                        .hasRole(COORDINADOR)
                        // Consultas: ambos roles (el filtrado por duenio lo hace el servicio)
                        .requestMatchers(HttpMethod.GET, "/api/solicitudes", "/api/solicitudes/**")
                        .hasAnyRole(ADOPTANTE, COORDINADOR)
                        .anyRequest().denyAll());
        return http.build();
    }
}

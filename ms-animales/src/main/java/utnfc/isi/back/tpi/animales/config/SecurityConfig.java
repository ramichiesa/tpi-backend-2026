package utnfc.isi.back.tpi.animales.config;

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
import static utnfc.isi.back.tpi.common.security.Roles.SERVICIO;

/**
 * El servicio valida el JWT por su cuenta (no confia en que el Gateway lo haya hecho)
 * y aplica la matriz de permisos de CONTRATOS.md, seccion 3.5.
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
                        // API interna: solo el token de servicio de Adopciones
                        .requestMatchers("/internal/**").hasRole(SERVICIO)
                        .requestMatchers(HttpMethod.GET, "/api/animales/**").hasAnyRole(ADOPTANTE, COORDINADOR)
                        .requestMatchers(HttpMethod.POST, "/api/animales/**").hasRole(COORDINADOR)
                        .requestMatchers(HttpMethod.PUT, "/api/animales/**").hasRole(COORDINADOR)
                        .anyRequest().denyAll());
        return http.build();
    }
}

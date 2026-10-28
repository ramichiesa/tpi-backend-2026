package utnfc.isi.back.tpi.common.security;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;

/**
 * Configuracion base que comparten los tres servicios como Resource Servers:
 * sin sesion (cada peticion trae su JWT), sin CSRF (no hay cookies), validacion del JWT
 * con el conversor de roles de Keycloak y respuestas 401/403 en el formato unico.
 *
 * Las reglas por ruta (quien puede hacer que) NO estan aca: cada servicio las define en su
 * propio SecurityConfig, porque dependen de su API.
 */
public final class ResourceServerDefaults {

    private ResourceServerDefaults() {
    }

    public static HttpSecurity aplicar(HttpSecurity http,
                                       JsonAuthenticationEntryPoint entryPoint,
                                       JsonAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new KeycloakRoleConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
    }
}

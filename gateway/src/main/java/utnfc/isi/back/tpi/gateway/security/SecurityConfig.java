package utnfc.isi.back.tpi.gateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import utnfc.isi.back.tpi.gateway.error.JsonErrorWriter;

/**
 * Security Filter del Gateway (requisito del enunciado).
 *
 * Responsabilidad: autenticacion en el borde. Valida firma, emisor y vencimiento del JWT
 * (Resource Server con las claves publicas de Keycloak) y solo deja pasar a /api/** tokens de
 * usuarios (ADOPTANTE o COORDINADOR). Un token de servicio (rol SERVICIO) no puede usar la API
 * publica. Sin token o con token invalido: 401; con rol insuficiente: 403.
 *
 * No es responsabilidad del Gateway decidir "quien puede aprobar una solicitud" ni "de quien es
 * una donacion": esas reglas finas las aplica cada servicio, que ademas vuelve a validar el JWT.
 * Asi la seguridad no depende de que la red interna sea inviolable (defensa en profundidad).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(new KeycloakRealmRolesConverter());
        jwtConverter.setPrincipalClaimName("sub");

        ServerAuthenticationEntryPoint entryPoint = (exchange, ex) -> JsonErrorWriter.escribir(exchange,
                HttpStatus.UNAUTHORIZED, "No autenticado", "Falta el token o es invalido");
        ServerAccessDeniedHandler accessDenied = (exchange, ex) -> JsonErrorWriter.escribir(exchange,
                HttpStatus.FORBIDDEN, "Prohibido", "No tiene permisos para realizar esta operacion");

        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .authorizeExchange(ex -> ex
                        .pathMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .pathMatchers("/api/**").hasAnyRole("ADOPTANTE", "COORDINADOR")
                        // Todo lo demas (incluida /internal/**, que no tiene ruta) queda cerrado
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(new ReactiveJwtAuthenticationConverterAdapter(jwtConverter)))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDenied))
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(accessDenied))
                .build();
    }
}

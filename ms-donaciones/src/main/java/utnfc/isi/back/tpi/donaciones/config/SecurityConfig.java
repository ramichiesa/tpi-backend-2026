package utnfc.isi.back.tpi.donaciones.config;

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

/** Matriz de permisos de ms-donaciones (CONTRATOS.md 3.5 y regla D-04). */
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
                        .requestMatchers(HttpMethod.POST, "/api/donaciones").hasRole(ADOPTANTE)
                        .requestMatchers(HttpMethod.GET, "/api/donaciones/total").hasRole(COORDINADOR)
                        .requestMatchers(HttpMethod.GET, "/api/donaciones", "/api/donaciones/**")
                        .hasAnyRole(ADOPTANTE, COORDINADOR)
                        .anyRequest().denyAll());
        return http.build();
    }
}

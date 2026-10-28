package utnfc.isi.back.tpi.gateway.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte realm_access.roles de Keycloak en autoridades ROLE_*.
 *
 * Es la misma logica que KeycloakRoleConverter de tpi-common. Se repite a proposito porque el
 * contrato decidio que el Gateway (reactivo) no dependa del JAR comun, pensado para servicios
 * Spring MVC (filtro de servlet, @RestControllerAdvice). Son 15 lineas sin reglas de negocio.
 */
public class KeycloakRealmRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (!(jwt.getClaims().get("realm_access") instanceof Map<?, ?> realmAccess)
                || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + ((String) r).toUpperCase(Locale.ROOT)))
                .toList();
    }
}

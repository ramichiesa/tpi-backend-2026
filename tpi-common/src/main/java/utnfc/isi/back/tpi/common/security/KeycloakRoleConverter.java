package utnfc.isi.back.tpi.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Convierte el JWT emitido por Keycloak en una autenticacion de Spring Security.
 *
 * Keycloak publica los roles de realm en el claim {@code realm_access.roles}; Spring Security
 * espera autoridades con el prefijo {@code ROLE_} para que funcionen {@code hasRole("X")}.
 * El nombre de la autenticacion es el claim {@code sub}, que es el identificador del usuario
 * usado como "duenio" de perfiles, solicitudes y donaciones.
 *
 * Tolera que el claim falte o tenga un formato inesperado: en ese caso el usuario queda
 * autenticado pero sin roles, y cualquier regla {@code hasRole} lo rechaza con 403.
 */
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    public static final String CLAIM_REALM_ACCESS = "realm_access";
    public static final String CLAIM_ROLES = "roles";

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, extraerAutoridades(jwt), jwt.getSubject());
    }

    public static Collection<GrantedAuthority> extraerAutoridades(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get(CLAIM_REALM_ACCESS);
        if (!(realmAccess instanceof Map<?, ?> mapa)) {
            return List.of();
        }
        Object roles = mapa.get(CLAIM_ROLES);
        if (!(roles instanceof Collection<?> lista)) {
            return List.of();
        }
        return lista.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(rol -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + rol.toUpperCase(Locale.ROOT)))
                .toList();
    }
}

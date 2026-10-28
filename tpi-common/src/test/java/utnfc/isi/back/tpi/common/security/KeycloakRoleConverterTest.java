package utnfc.isi.back.tpi.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRoleConverterTest {

    private final KeycloakRoleConverter converter = new KeycloakRoleConverter();

    private static Jwt jwtCon(Map<String, Object> claims) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("usuario-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void convierteRolesDeRealmEnAutoridadesConPrefijo() {
        Jwt jwt = jwtCon(Map.of("realm_access", Map.of("roles", List.of("ADOPTANTE", "offline_access"))));

        AbstractAuthenticationToken auth = converter.convert(jwt);

        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADOPTANTE", "ROLE_OFFLINE_ACCESS");
        assertThat(auth.getName()).isEqualTo("usuario-123");
    }

    @Test
    void sinClaimRealmAccessQuedaSinRoles() {
        AbstractAuthenticationToken auth = converter.convert(jwtCon(Map.of("otro", "valor")));

        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void claimConFormatoInesperadoNoRompe() {
        Jwt jwt = jwtCon(Map.of("realm_access", Map.of("roles", "COORDINADOR")));

        assertThat(converter.convert(jwt).getAuthorities()).isEmpty();
    }

    @Test
    void ignoraElementosQueNoSonTexto() {
        Jwt jwt = jwtCon(Map.of("realm_access", Map.of("roles", List.of("coordinador", 7))));

        assertThat(converter.convert(jwt).getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_COORDINADOR");
    }
}

package utnfc.isi.back.tpi.adopciones.controller;

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import utnfc.isi.back.tpi.adopciones.service.Usuario;
import utnfc.isi.back.tpi.common.security.Roles;

/** Traduce la autenticacion de Spring Security (JWT ya validado) al {@link Usuario} del dominio. */
final class UsuarioActual {

    private UsuarioActual() {
    }

    static Usuario de(JwtAuthenticationToken auth) {
        boolean coordinador = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_" + Roles.COORDINADOR));
        return new Usuario(auth.getToken().getSubject(), coordinador);
    }
}

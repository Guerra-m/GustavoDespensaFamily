package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.model.Usuario;
import com.guerram.DespensaFamily.repository.UsuarioRepository;
import com.guerram.DespensaFamily.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String token = tokenDeLaPeticion(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Usuario> usuario = usuarioDelToken(token);

        if (usuario.isPresent()) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    usuario.get().getUsername(), null, authoritiesDe(usuario.get())));
        } else {
            // Token roto, vencido, o de un usuario que ya no existe: el pedido
            // sigue como anonimo, sin cortarlo aca. Asi lo publico (la
            // pantalla de login, sus iconos, /usuario/login y
            // /usuario/logout) sigue andando con una cookie vieja en el
            // navegador — antes, un token invalido daba 401 hasta en el
            // logout, que es justamente lo que tiene que poder limpiarla. Si
            // la ruta pide sesion, SesionInvalidaEntryPoint contesta 401 (el
            // frontend manda al login) y borra la cookie.
            SesionInvalidaEntryPoint.marcar(request);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * DF-SEC-002 (SESS-06): el token solo dice QUIEN es. Si todavia existe y
     * que puede hacer sale de la base en cada pedido.
     *
     * <p>Antes rol y permisos venian grabados en el token, que vive 24 h:
     * borrar a un empleado no le cortaba el acceso (se reprodujo una venta con
     * la cookie de un usuario ya borrado), quitarle PERM_CAJA no le sacaba la
     * caja, y un permiso nuevo no servia hasta volver a entrar. La consulta es
     * un SELECT por clave unica contra el H2 local: el costo es despreciable
     * al lado de dejar entrar a alguien que el dueño ya saco.
     */
    private Optional<Usuario> usuarioDelToken(String token) {
        String username;
        try {
            username = jwtService.validateToken(token).getSubject();
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
        if (username == null) {
            return Optional.empty();
        }
        return usuarioRepository.findByUsername(username);
    }

    /**
     * Rol + permisos sueltos del empleado (ver Usuario.java): dos authorities
     * mas, aparte del rol, para que SecurityConfig pueda dejar pasar a un
     * EMPLEADO puntual sin ascenderlo a ADMIN.
     */
    private static List<SimpleGrantedAuthority> authoritiesDe(Usuario usuario) {
        // Mismo criterio que el login (UsuarioService.login): sin rol, EMPLEADO.
        String rol = usuario.getRol() != null ? usuario.getRol() : "EMPLEADO";
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
        if (usuario.isPuedeGestionarProductos()) {
            authorities.add(new SimpleGrantedAuthority("PERM_STOCK"));
        }
        if (usuario.isPuedeVerCaja()) {
            authorities.add(new SimpleGrantedAuthority("PERM_CAJA"));
        }
        return authorities;
    }

    /**
     * Primero la cookie, despues la cabecera.
     *
     * <p>La cookie es como entra el navegador: la manda sola en cada pedido,
     * incluidos los de las imagenes, que es lo que una etiqueta {@code <img>}
     * nunca pudo hacer con una cabecera. Ver {@link CookieDeSesion}.
     *
     * <p>La cabecera se mantiene para los clientes que no son un navegador
     * —curl, un script, una prueba—. No debilita nada: un XSS no puede leer la
     * cookie HttpOnly, asi que tampoco puede armar la cabecera. Lo que si seria
     * un error es que el <i>frontend</i> guardara el token en los dos lados a
     * la vez (FE-02), y por eso el login ya no lo devuelve en el cuerpo.
     */
    private String tokenDeLaPeticion(HttpServletRequest request) {
        String deLaCookie = CookieDeSesion.token(request);
        if (deLaCookie != null) {
            return deLaCookie;
        }
        String cabecera = request.getHeader("Authorization");
        return cabecera != null && cabecera.startsWith("Bearer ") ? cabecera.substring(7) : null;
    }
}

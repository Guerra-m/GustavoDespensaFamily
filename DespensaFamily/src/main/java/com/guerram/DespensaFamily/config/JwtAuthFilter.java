package com.guerram.DespensaFamily.config;

import com.guerram.DespensaFamily.service.JwtService;
import io.jsonwebtoken.Claims;
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

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        final String token = tokenDeLaPeticion(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtService.validateToken(token);

            String username = claims.getSubject();
            String rol = claims.get("rol", String.class);

            // Permisos sueltos del empleado (ver Usuario.java): dos
            // authorities más, aparte del rol, para que SecurityConfig pueda
            // dejar pasar a un EMPLEADO puntual sin ascenderlo a ADMIN.
            List<SimpleGrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
            if (Boolean.TRUE.equals(claims.get("stock", Boolean.class))) {
                authorities.add(new SimpleGrantedAuthority("PERM_STOCK"));
            }
            if (Boolean.TRUE.equals(claims.get("caja", Boolean.class))) {
                authorities.add(new SimpleGrantedAuthority("PERM_CAJA"));
            }

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    username,
                    null,
                    authorities
            );

            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
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

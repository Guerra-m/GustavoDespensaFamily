package com.guerram.DespensaFamily.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Que contestar cuando una ruta pide sesion y el pedido llega sin una valida.
 *
 * <ul>
 *   <li><b>Traia sesion y ya no vale</b> (token roto o vencido, o el usuario
 *       fue borrado — DF-SEC-002): 401 y la cookie borrada. El frontend trata
 *       el 401 como "sesion vencida" y manda al login, que es lo que tiene que
 *       pasarle al empleado que el dueño acaba de sacar.</li>
 *   <li><b>Nunca trajo sesion</b>: 403, como siempre (es lo que hacia el
 *       entry point por defecto de Spring). No se cambia porque no hay nada
 *       que limpiar y los tests de permisos cuentan con ese 403.</li>
 * </ul>
 *
 * <p>La marca la pone {@link JwtAuthFilter}: el filtro no corta el pedido,
 * para que lo publico (login, logout, la pantalla de login) siga andando con
 * una cookie vieja en el navegador.
 */
public class SesionInvalidaEntryPoint implements AuthenticationEntryPoint {

    private static final String MARCA = SesionInvalidaEntryPoint.class.getName() + ".sesionInvalida";

    static void marcar(HttpServletRequest request) {
        request.setAttribute(MARCA, Boolean.TRUE);
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        if (!Boolean.TRUE.equals(request.getAttribute(MARCA))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied");
            return;
        }
        response.addCookie(CookieDeSesion.borrar(request));
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"La sesión ya no es válida. Volvé a iniciar sesión.\"}");
    }
}

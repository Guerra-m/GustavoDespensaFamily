package com.guerram.DespensaFamily.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Defensa CSRF: rechaza los pedidos que <b>cambian algo</b> y vienen declarados
 * desde otro sitio.
 *
 * <p>Hace falta desde que la sesion viaja en una cookie. Con el token en una
 * cabecera, un sitio ajeno no podia autenticarse aunque hiciera el pedido: no
 * tenia el token. Con una cookie, el navegador la manda sola — tambien cuando
 * quien dispara el pedido es una pagina cualquiera que la victima tenia abierta
 * (SESS-10).
 *
 * <p>Van dos defensas y no una. {@code SameSite=Strict} lo frena en el
 * navegador, que es donde mas barato sale; esto lo frena en el servidor, que es
 * donde no depende de que el navegador se porte bien.
 *
 * <p>Dos decisiones que parecen agujeros y no lo son:
 *
 * <ul>
 *   <li><b>GET no se controla.</b> Un GET no cambia nada, y filtrarlo por
 *       origen solo romperia cosas legitimas. Lo que protege la lectura es la
 *       cookie, no el origen.
 *   <li><b>Sin cabecera Origin se deja pasar.</b> curl, un script, el propio
 *       instalador: ninguno manda Origin. Un navegador, en cambio, la manda
 *       <i>siempre</i> que el pedido salga de otro sitio, que es justo el caso
 *       que hay que frenar. Rechazar por ausencia solo romperia a los clientes
 *       que no son el ataque.
 * </ul>
 */
@Component
public class FiltroDeOrigen extends OncePerRequestFilter {

    private static final Set<String> METODOS_QUE_LEEN = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    /**
     * En desarrollo el frontend vive en el puerto de Vite y el backend en otro,
     * asi que el Origin nunca coincide con el del pedido. Son los mismos
     * origenes que declara CorsConfig.
     */
    private final List<String> origenesDeDesarrollo;

    public FiltroDeOrigen(@Value("${despensa.origenes-permitidos:}") String origenes) {
        this.origenesDeDesarrollo = origenes == null || origenes.isBlank()
                ? List.of()
                : List.of(origenes.split("\\s*,\\s*"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion,
                                    HttpServletResponse respuesta,
                                    FilterChain cadena) throws ServletException, IOException {

        if (!esDeConfianza(peticion)) {
            respuesta.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Origen no permitido para una operacion que modifica datos");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private boolean esDeConfianza(HttpServletRequest peticion) {
        if (METODOS_QUE_LEEN.contains(peticion.getMethod())) {
            return true;
        }
        String origen = peticion.getHeader("Origin");
        if (origen == null || origen.isBlank()) {
            return true;
        }
        return origen.equals(origenPropio(peticion)) || origenesDeDesarrollo.contains(origen);
    }

    /**
     * El origen de la propia aplicacion, reconstruido del pedido. Se arma asi y
     * no con una constante porque la misma instalacion se abre de varias formas:
     * localhost desde la ventana de escritorio, y la IP del local desde el
     * celular. Las dos son legitimas y las dos tienen que coincidir consigo
     * mismas.
     */
    private String origenPropio(HttpServletRequest peticion) {
        String esquema = peticion.getScheme();
        int puerto = peticion.getServerPort();
        boolean puertoPorDefecto = ("http".equals(esquema) && puerto == 80)
                || ("https".equals(esquema) && puerto == 443);

        return puertoPorDefecto
                ? esquema + "://" + peticion.getServerName()
                : esquema + "://" + peticion.getServerName() + ":" + puerto;
    }
}

package com.guerram.DespensaFamily.config;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * La cookie donde viaja la sesion.
 *
 * <p>Antes el token vivia en {@code localStorage} y cada pedido lo mandaba en
 * la cabecera {@code Authorization}. Se cambio por dos motivos, y el primero es
 * un bug que se veia en la pantalla:
 *
 * <ol>
 *   <li><b>Las fotos de producto no se veian.</b> La auditoria del 01/09/2026
 *       dejo {@code /imagenes/**} como {@code authenticated()}, con buen
 *       criterio. Pero una etiqueta {@code <img src="...">} no puede mandar una
 *       cabecera: el navegador pide la imagen sin {@code Authorization} y se
 *       come un 403. Desde el 01/09 no se veia ninguna foto, ni en Productos ni
 *       en la caja. Una cookie, en cambio, el navegador la manda sola en cada
 *       pedido del dominio, imagenes incluidas.
 *   <li><b>El token era robable por XSS.</b> Lo marco la misma auditoria: con
 *       el token en localStorage, cualquier script inyectado lo lee con una
 *       linea. Una cookie {@code HttpOnly} no es legible desde JavaScript bajo
 *       ninguna circunstancia (FE-02).
 * </ol>
 *
 * <p>El costo de pasar a cookies es CSRF: el navegador la manda sola, tambien
 * cuando el pedido lo dispara otro sitio. Por eso van juntas
 * {@code SameSite=Strict} (lo frena el navegador) y {@link FiltroDeOrigen} (lo
 * frena el servidor). SESS-10.
 */
public final class CookieDeSesion {

    public static final String NOMBRE = "sesion";

    /** La misma que el JWT que lleva adentro (JwtService.EXPIRATION_MS). */
    private static final int DURACION_SEGUNDOS = 24 * 60 * 60;

    private CookieDeSesion() {
    }

    public static Cookie crear(String token, HttpServletRequest peticion) {
        Cookie cookie = new Cookie(NOMBRE, token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(DURACION_SEGUNDOS);

        // Strict y no Lax: no hay ningun flujo que aterrice logueado desde
        // afuera (no hay magic link por mail), asi que no se pierde nada.
        cookie.setAttribute("SameSite", "Strict");

        // Secure se decide por como llego la peticion, no por una constante.
        // Hoy la app corre en HTTP sobre la red del local: con Secure fijo en
        // true el navegador nunca mandaria la cookie y no entraria nadie. Si
        // algun dia se le pone HTTPS adelante, esto se activa solo.
        cookie.setSecure(peticion.isSecure());

        return cookie;
    }

    /**
     * La cookie que borra a la anterior. Cerrar sesion tiene que apagarla del
     * lado del servidor: si solo la borrara el cliente, bastaria con no correr
     * ese codigo para conservarla.
     */
    public static Cookie borrar(HttpServletRequest peticion) {
        Cookie cookie = new Cookie(NOMBRE, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        cookie.setSecure(peticion.isSecure());
        return cookie;
    }

    /** El token que trae la peticion en la cookie, o null si no hay. */
    public static String token(HttpServletRequest peticion) {
        Cookie[] cookies = peticion.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NOMBRE.equals(cookie.getName()) && cookie.getValue() != null
                    && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

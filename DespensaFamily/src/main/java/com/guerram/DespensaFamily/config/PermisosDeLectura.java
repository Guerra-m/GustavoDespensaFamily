package com.guerram.DespensaFamily.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * DF-SEC-003: que puede LEER cada uno, con las mismas authorities que arma
 * JwtAuthFilter desde la base en cada pedido.
 *
 * <p>SecurityConfig decide a que rutas se entra; esto decide que se ve adentro
 * de una ruta que comparten todos (GET /productos, GET /ventas): mismo endpoint,
 * distinta respuesta segun quien pregunta (API-04, AUTZ-09).
 *
 * <ul>
 *   <li>Costos (precio de costo, margen, proveedor): el dueño y quien gestiona
 *       productos (PERM_STOCK), que los necesita para cargar precios.</li>
 *   <li>Todas las ventas: el dueño y el cajero (PERM_CAJA), que ve la plata
 *       del negocio. El resto ve solo las suyas del dia.</li>
 * </ul>
 */
public final class PermisosDeLectura {

    private PermisosDeLectura() {
    }

    public static boolean veCostos(Authentication quien) {
        return tiene(quien, "ROLE_ADMIN") || tiene(quien, "PERM_STOCK");
    }

    public static boolean veTodasLasVentas(Authentication quien) {
        return tiene(quien, "ROLE_ADMIN") || tiene(quien, "PERM_CAJA");
    }

    private static boolean tiene(Authentication quien, String authority) {
        if (quien == null) return false;
        for (GrantedAuthority a : quien.getAuthorities()) {
            if (authority.equals(a.getAuthority())) return true;
        }
        return false;
    }
}

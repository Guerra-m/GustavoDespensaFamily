package com.guerram.DespensaFamily.config;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Politica de retencion de respaldos.
 *
 * <p>Deliberadamente conservadora: solo entra en la cuenta —y solo se borra—
 * lo que la propia aplicacion genero. Cualquier otro archivo que aparezca en la
 * carpeta (una copia hecha a mano antes de una migracion, algo que dejo el
 * usuario) queda intacto. Es la unica copia de los datos del negocio.
 */
public final class Respaldos {

    /** Igual al formato que arma {@link Escritorio#nombreDeRespaldo}. */
    private static final Pattern NOMBRE = Pattern.compile("despensa-\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}\\.zip");

    private Respaldos() {
    }

    public static boolean esRespaldo(String nombre) {
        return nombre != null && NOMBRE.matcher(nombre).matches();
    }

    /**
     * Devuelve los respaldos que sobran, del mas viejo al mas nuevo.
     *
     * <p>El nombre lleva la fecha en formato ordenable, asi que alcanza con
     * ordenar alfabeticamente: no hay que parsear ni mirar la fecha del archivo
     * en disco, que cambia si alguien lo copia.
     */
    public static List<String> aEliminar(Collection<String> archivos, int aConservar) {
        if (aConservar <= 0) {
            return List.of();
        }
        List<String> respaldos = archivos.stream()
                .filter(Respaldos::esRespaldo)
                .sorted()
                .toList();

        int sobran = respaldos.size() - aConservar;
        return sobran <= 0 ? List.of() : List.copyOf(respaldos.subList(0, sobran));
    }
}

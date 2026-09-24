package com.guerram.DespensaFamily.actualizacion;

/**
 * Una version del programa, para poder compararlas por numero.
 *
 * <p>Existe por un solo motivo: comparar "1.10.0" con "1.9.0" como texto da el
 * resultado al reves, y de esa comparacion depende que el cartel de
 * actualizacion aparezca cuando hay algo nuevo y desaparezca despues de
 * actualizar.
 *
 * <p>Una version que no se entiende no es un error que rompa nada: se comporta
 * como "no comparable", y el efecto es que no se ofrece ninguna actualizacion.
 * Preferimos quedarnos callados antes que ofrecer actualizar a algo que no
 * sabemos que es.
 */
public final class Version {

    /** Sufijos que no cambian el orden: la "v" de GitHub y el -SNAPSHOT. */
    private static final String LIMPIEZA = "^[vV]|[-+].*$";

    private static final Version ILEGIBLE = new Version(null);

    private final int[] partes;

    private Version(int[] partes) {
        this.partes = partes;
    }

    public static Version de(String texto) {
        if (texto == null || texto.isBlank()) {
            return ILEGIBLE;
        }
        String limpio = texto.trim().replaceAll(LIMPIEZA, "");
        if (limpio.isEmpty()) {
            return ILEGIBLE;
        }
        String[] trozos = limpio.split("\\.");
        int[] numeros = new int[trozos.length];
        for (int i = 0; i < trozos.length; i++) {
            try {
                numeros[i] = Integer.parseInt(trozos[i]);
            } catch (NumberFormatException e) {
                return ILEGIBLE;
            }
        }
        return new Version(numeros);
    }

    public boolean esPosteriorA(Version otra) {
        if (partes == null || otra.partes == null) {
            return false;
        }
        int largo = Math.max(partes.length, otra.partes.length);
        for (int i = 0; i < largo; i++) {
            // Las partes que faltan valen cero: "1.2" y "1.2.0" son la misma.
            int mia = i < partes.length ? partes[i] : 0;
            int suya = i < otra.partes.length ? otra.partes[i] : 0;
            if (mia != suya) {
                return mia > suya;
            }
        }
        return false;
    }
}

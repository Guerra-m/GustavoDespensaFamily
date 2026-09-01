package com.guerram.DespensaFamily.config;

import java.security.SecureRandom;

/**
 * Contrasena inicial del administrador, distinta en cada instalacion.
 *
 * <p>Antes el primer administrador se creaba con {@code admin123} escrito en el
 * codigo: la misma en todas las copias, publica, y suficiente para entrar desde
 * cualquier telefono conectado al wifi del local.
 *
 * <p>Esta contrasena la tiene que leer una persona —de un cartel al terminar de
 * instalar, o dictada por telefono— asi que el alfabeto deja afuera los
 * caracteres que se confunden (0/O, 1/l/I, 5/S) y el valor viene partido en
 * grupos de cuatro. Lo que se pierde en entropia por caracter se recupera con
 * el largo.
 */
public final class CredencialInicial {

    /**
     * Sin 0, O, 1, l, I, 5 ni S: son los errores de tipeo garantizados cuando
     * alguien copia una clave a mano. Tampoco hay simbolos, que complican
     * dictarla y ademas romperian el archivo de propiedades donde termina.
     */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRTUVWXYZabcdefghijkmnopqrstuvwxyz2346789";

    private static final int GRUPOS = 4;
    private static final int LARGO_GRUPO = 4;

    private static final SecureRandom AZAR = new SecureRandom();

    private CredencialInicial() {
    }

    /** Devuelve algo con la forma {@code aB3d-Kp9m-Rt4w-Xy7z}. */
    public static String generar() {
        StringBuilder salida = new StringBuilder();
        for (int grupo = 0; grupo < GRUPOS; grupo++) {
            if (grupo > 0) {
                salida.append('-');
            }
            for (int i = 0; i < LARGO_GRUPO; i++) {
                salida.append(ALFABETO.charAt(AZAR.nextInt(ALFABETO.length())));
            }
        }
        return salida.toString();
    }
}

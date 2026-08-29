package com.guerram.DespensaFamily.util;

/**
 * Redondeo de cantidades al gramo.
 *
 * <p>El stock y las cantidades viajan como {@code double}, y el double no sabe
 * representar 0,1 en binario: restarle 1,538 a 5 no da 3,462 sino
 * 3.4619999999999997, y eso salia tal cual en la tarjeta del producto.
 *
 * <p>La fraccion minima del negocio es el gramo (0,001), asi que redondear a
 * tres decimales no pierde nada real y arregla el problema en su origen sin
 * tener que migrar la columna a BigDecimal.
 */
public final class Cantidades {

    /** Tres decimales: el gramo, que es la fraccion minima de un producto por peso. */
    private static final double FACTOR = 1000.0;

    private Cantidades() {
    }

    public static double redondear(double cantidad) {
        // NaN e infinito no se pueden redondear: los deja pasar para que la
        // validacion de cada servicio decida, en vez de convertirlos en un 0
        // que parece un numero valido.
        if (!Double.isFinite(cantidad)) {
            return cantidad;
        }
        return Math.round(cantidad * FACTOR) / FACTOR;
    }
}

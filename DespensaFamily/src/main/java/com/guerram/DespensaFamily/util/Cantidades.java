package com.guerram.DespensaFamily.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Redondeo de cantidades al gramo.
 *
 * <p>DF-DEBT-001: migrado de double a BigDecimal. El double no sabe
 * representar 0,1 en binario: restarle 1,538 a 5 no daba 3,462 sino
 * 3.4619999999999997, y eso salia tal cual en la tarjeta del producto.
 * BigDecimal con escala fija de 3 decimales evita esa cola binaria en vez de
 * solo redondearla despues.
 *
 * <p>La fraccion minima del negocio es el gramo (0,001), asi que redondear a
 * tres decimales no pierde nada real.
 */
public final class Cantidades {

    /** Tres decimales: el gramo, que es la fraccion minima de un producto por peso. */
    public static final int ESCALA = 3;

    private static final RoundingMode MODO = RoundingMode.HALF_UP;

    private Cantidades() {
    }

    /** Redondea al gramo. Null pasa a traves: que decida la validacion de cada service. */
    public static BigDecimal redondear(BigDecimal cantidad) {
        if (cantidad == null) {
            return null;
        }
        return cantidad.setScale(ESCALA, MODO);
    }
}

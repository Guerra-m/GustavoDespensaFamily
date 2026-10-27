package com.guerram.DespensaFamily.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Redondeo de importes al centavo.
 *
 * <p>DF-DEBT-001: migrado de double a BigDecimal. El double no representa
 * los decimales exactos en base 10 — un costo de 123,45 con 33% de ganancia
 * daba un precio de venta de {@code 164.1885}: cuatro decimales, que salian
 * tal cual en la pantalla y en el ticket. BigDecimal con escala fija saca el
 * problema de raiz en vez de solo redondear el sintoma.
 *
 * <p>Existe como clase aparte porque el precio de venta se calcula en dos
 * lugares —al registrar una compra y al aplicar la ganancia global— y el
 * redondeo estaba en uno solo. Con la cuenta en un unico lado, la proxima
 * correccion no puede volver a quedar a medias.
 */
public final class Dinero {

    /** Dos decimales: el centavo. Unico lugar que define la escala de la plata. */
    public static final int ESCALA = 2;

    private static final RoundingMode MODO = RoundingMode.HALF_UP;

    private Dinero() {
    }

    /** Redondea al centavo. Null pasa a traves: que decida la validacion de cada service. */
    public static BigDecimal redondear(BigDecimal importe) {
        if (importe == null) {
            return null;
        }
        return importe.setScale(ESCALA, MODO);
    }

    /** Precio de venta a partir del costo y el porcentaje de ganancia. */
    public static BigDecimal precioConGanancia(BigDecimal costo, BigDecimal porcentajeGanancia) {
        if (costo == null || porcentajeGanancia == null) {
            return null;
        }
        BigDecimal ganancia = costo.multiply(porcentajeGanancia)
                .divide(BigDecimal.valueOf(100), 10, MODO);
        return redondear(costo.add(ganancia));
    }
}

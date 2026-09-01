package com.guerram.DespensaFamily.util;

/**
 * Redondeo de importes al centavo.
 *
 * <p>El equivalente de {@link Cantidades} para la plata. Un costo de 123,45 con
 * 33% de ganancia daba un precio de venta de {@code 164.1885}: cuatro
 * decimales, que salian tal cual en la pantalla y en el ticket.
 *
 * <p>Existe como clase aparte porque el precio de venta se calcula en dos
 * lugares —al registrar una compra y al aplicar la ganancia global— y el
 * redondeo estaba en uno solo. Con la cuenta en un unico lado, la proxima
 * correccion no puede volver a quedar a medias.
 */
public final class Dinero {

    /** Dos decimales: el centavo. */
    private static final double FACTOR = 100.0;

    private Dinero() {
    }

    public static double redondear(double importe) {
        if (!Double.isFinite(importe)) {
            return importe;
        }
        return Math.round(importe * FACTOR) / FACTOR;
    }

    /** Precio de venta a partir del costo y el porcentaje de ganancia. */
    public static double precioConGanancia(double costo, double porcentajeGanancia) {
        return redondear(costo + (costo * porcentajeGanancia / 100));
    }
}

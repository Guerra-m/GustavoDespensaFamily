package com.guerram.DespensaFamily.model;

/**
 * Que explica un movimiento de caja. Es lo que hace que "salieron $8.000" se
 * conteste con "porque se le pago a un proveedor" y no quede como un numero
 * suelto.
 */
public enum CategoriaMovimiento {
    /** El ingreso que genera una venta al cobrarse. */
    VENTA,
    /** El egreso que genera una compra pagada al recibirla o al pagarla despues. */
    COMPRA,
    /** Gasto operativo sin comprobante propio en el sistema: luz, alquiler, insumos. */
    GASTO,
    /** El dueño saca plata del cajon para uso personal, sin que sea un gasto del negocio. */
    RETIRO,
    /** El dueño (u otra persona) mete plata propia al cajon. */
    APORTE,
    /** Diferencia detectada al contar el cajon contra lo que la caja esperaba (ver arqueo). */
    AJUSTE,
    /** Pago a un empleado. Categoria propia y no GASTO: es el egreso mas
     * grande y regular de un negocio chico, y mezclarlo con la factura de
     * la luz vuelve inutil el reporte de gastos operativos. */
    SUELDO
}

package com.guerram.DespensaFamily.model;

/**
 * Como se movio la plata. Es lo que distingue el cajon fisico (EFECTIVO) del
 * dinero electronico (TARJETA, TRANSFERENCIA): "ventas totales" y "efectivo
 * disponible" no son la misma pregunta, y este campo es lo que permite
 * contestar las dos con la misma tabla.
 */
public enum MetodoPago {
    EFECTIVO,
    TARJETA,
    TRANSFERENCIA,
    OTRO
}

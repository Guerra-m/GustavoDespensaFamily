package com.guerram.DespensaFamily.model;

/** Direccion del movimiento. Redundante con el signo del monto a proposito:
 * el monto siempre se guarda positivo, y este campo es el unico lugar donde
 * se decide si suma o resta — un solo lugar donde equivocarse en vez de dos. */
public enum TipoMovimiento {
    INGRESO,
    EGRESO
}

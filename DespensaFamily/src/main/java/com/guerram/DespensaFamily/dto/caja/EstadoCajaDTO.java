package com.guerram.DespensaFamily.dto.caja;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Lo que la pantalla de Caja muestra de un vistazo (seccion 6 del mapa):
 * saldo total, cuanto hay en efectivo de verdad, cuanto se cobro
 * electronico, y como vino el dia.
 */
@Getter
@Builder
@AllArgsConstructor
public class EstadoCajaDTO {
    /** Todo lo que hay, sumando efectivo y electronico. */
    private double saldoTotal;
    /** Lo que realmente esta en el cajon: solo movimientos EFECTIVO. */
    private double efectivoEnCaja;
    /** Cobrado con tarjeta o transferencia: no esta fisicamente en el local. */
    private double saldoElectronico;
    private double ingresosDeHoy;
    /** DF-UX-006: ya NO incluye anulaciones -- solo gastos, retiros, compras y ajustes reales del dia. */
    private double egresosDeHoy;
    private double resultadoDeHoy;
    /** Ventas de hoy netas de sus propias anulaciones (venta - reversion de esa venta, si la hubo). */
    private double ventasNetasDeHoy;
    /** Cuanto se anulo hoy (cualquier categoria), en valor absoluto -- aparte de "egresos", no mezclado. */
    private double anulacionesDeHoy;
}

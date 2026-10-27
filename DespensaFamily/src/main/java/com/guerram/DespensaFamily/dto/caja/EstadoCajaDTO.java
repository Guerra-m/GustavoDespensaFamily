package com.guerram.DespensaFamily.dto.caja;

import java.math.BigDecimal;

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
    private BigDecimal saldoTotal;
    /** Lo que realmente esta en el cajon: solo movimientos EFECTIVO. */
    private BigDecimal efectivoEnCaja;
    /** Cobrado con tarjeta o transferencia: no esta fisicamente en el local. */
    private BigDecimal saldoElectronico;
    private BigDecimal ingresosDeHoy;
    /** DF-UX-006: ya NO incluye anulaciones -- solo gastos, retiros, compras y ajustes reales del dia. */
    private BigDecimal egresosDeHoy;
    private BigDecimal resultadoDeHoy;
    /** Ventas de hoy netas de sus propias anulaciones (venta - reversion de esa venta, si la hubo). */
    private BigDecimal ventasNetasDeHoy;
    /** Cuanto se anulo hoy (cualquier categoria), en valor absoluto -- aparte de "egresos", no mezclado. */
    private BigDecimal anulacionesDeHoy;
}

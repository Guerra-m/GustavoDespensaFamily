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
    private double egresosDeHoy;
    private double resultadoDeHoy;
}

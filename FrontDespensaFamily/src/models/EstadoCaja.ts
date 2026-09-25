export interface EstadoCaja {
  saldoTotal: number;
  /** Lo que realmente está en el cajón: solo movimientos EFECTIVO. */
  efectivoEnCaja: number;
  /** Cobrado con tarjeta o transferencia: no está físicamente en el local. */
  saldoElectronico: number;
  ingresosDeHoy: number;
  /** DF-UX-006: ya NO incluye anulaciones -- solo gastos, retiros, compras y ajustes reales del día. */
  egresosDeHoy: number;
  resultadoDeHoy: number;
  /** Ventas de hoy netas de sus propias anulaciones. */
  ventasNetasDeHoy: number;
  /** Cuánto se anuló hoy (cualquier categoría), en valor absoluto. */
  anulacionesDeHoy: number;
}

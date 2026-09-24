export interface EstadoCaja {
  saldoTotal: number;
  /** Lo que realmente está en el cajón: solo movimientos EFECTIVO. */
  efectivoEnCaja: number;
  /** Cobrado con tarjeta o transferencia: no está físicamente en el local. */
  saldoElectronico: number;
  ingresosDeHoy: number;
  egresosDeHoy: number;
  resultadoDeHoy: number;
}

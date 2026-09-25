/**
 * Respuesta de POST /compras/previsualizar — ver CompraService.previsualizar
 * en el backend. DF-RISK-001: precio de venta actual vs. el que resultaría
 * de confirmar la compra, calculado con la MISMA fórmula que usa
 * CompraService.registrarCompra (no se duplica la cuenta acá).
 */
export interface LineaCompraCotizada {
  productoId: number;
  productoNombre: string;
  precioCostoActual: number;
  precioVentaActual: number;
  precioVentaNuevo: number;
  /** true si el cambio supera el 50% — la auditoría pide resaltarlo. */
  cambioSignificativo: boolean;
}

export interface CompraCotizacion {
  lineas: LineaCompraCotizada[];
}

// DF-BUG-001: lo que devuelve POST /ventas/cotizar. Mismo shape que
// LineaCotizadaDTO/CotizacionDTO del backend (VentaService.cotizar) — es la
// UNICA fuente de precios que usa el carrito, no hay un segundo cálculo acá.
export interface LineaCotizada {
  productoId: number;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
  /** null si no hay descuento por % activo para esta línea. */
  descuentoPorcentaje: number | null;
  /** null si no hay combo activo. Ej: "3x2". */
  promoDescripcion: string | null;
  /** true si al cotizar no había stock suficiente para la cantidad pedida. */
  stockInsuficiente: boolean;
}

export interface Cotizacion {
  lineas: LineaCotizada[];
  total: number;
}

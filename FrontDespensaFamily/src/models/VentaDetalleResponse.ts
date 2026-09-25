import type { MetodoPago } from "./MetodoPago";

/**
 * Lo que devuelve GET /ventas/{id}/detalle.
 *
 * Este modelo estaba mal: declaraba `productoNombre` y `precio`, campos que el
 * backend nunca mandó. No se notaba porque el servicio devolvía `Promise<any>`
 * y el tipo no llegaba a usarse; el código real siempre leyó `productoId` y
 * `precioUnitario`, que son los que sí existen. Ahora refleja el DTO de verdad
 * (DetalleVentaDTO / VentaDetalleResponseDTO).
 */
export interface DetalleVentaResponse {
  id: number;
  cantidad: number;
  precioUnitario: number;
  // Solo para quien ve costos (dueño o PERM_STOCK): si no, no viene. DF-SEC-003.
  precioCosto?: number;
  subtotal: number;
  /** null si la linea no tuvo descuento por % (incluye ventas anteriores a V15). */
  descuentoPorcentaje?: number | null;
  /** null si la linea no tuvo combo/promo aplicada. */
  promoDescripcion?: string | null;
  ventaId: number;
  productoId: number;
}

export interface VentaDetalleResponse {
  id: number;
  /** ISO con hora. */
  fechaHora: string;
  total: number;
  metodoPago?: MetodoPago;
  estado?: "ACTIVA" | "ANULADA";
  usuario?: string | null;
  anuladaPor?: string | null;
  fechaAnulacion?: string | null;
  detalles: DetalleVentaResponse[];
}

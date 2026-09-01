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
  precioCosto: number;
  subtotal: number;
  ventaId: number;
  productoId: number;
}

export interface VentaDetalleResponse {
  id: number;
  /** ISO con hora. */
  fechaHora: string;
  total: number;
  estado?: "ACTIVA" | "ANULADA";
  usuario?: string | null;
  anuladaPor?: string | null;
  fechaAnulacion?: string | null;
  detalles: DetalleVentaResponse[];
}

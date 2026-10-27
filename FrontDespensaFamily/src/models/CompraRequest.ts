import type { MetodoPago } from "./MetodoPago";

export interface CompraRequest {
  proveedor: string;
  detalles: DetalleCompraRequest[];
  /** false = a cuenta: entra el stock pero no sale plata de caja todavía. */
  pagada: boolean;
  metodoPago: MetodoPago;
}

export interface DetalleCompraRequest {
  productoId: number;
  cantidad: number;
  precioCosto: number;
}

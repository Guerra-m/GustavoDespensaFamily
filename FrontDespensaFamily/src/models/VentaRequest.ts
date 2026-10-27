import type { DetalleVentaRequest } from "./DetalleVentaRequest";
import type { MetodoPago } from "./MetodoPago";

export interface VentaRequest {
  detalles: DetalleVentaRequest[];
  metodoPago: MetodoPago;
  /** Opcional, texto libre — sale en el ticket. */
  cliente?: string;
}

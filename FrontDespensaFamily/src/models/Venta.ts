// Venta.ts
import type { DetalleVenta } from "./DetalleVenta";
import type { MetodoPago } from "./MetodoPago";

export interface Venta {
    id?: number;
    /** ISO con hora ("2026-09-01T14:23:45") desde que la venta guarda el momento. */
    fechaHora: string;
    total: number;
    metodoPago?: MetodoPago;
    /** Opcional, texto libre — sale en el ticket. */
    cliente?: string | null;
    estado?: "ACTIVA" | "ANULADA";
    /** Quién la hizo y, si corresponde, quién la anuló. */
    usuario?: string | null;
    anuladaPor?: string | null;
    fechaAnulacion?: string | null;
    detalles?: DetalleVenta[];
}

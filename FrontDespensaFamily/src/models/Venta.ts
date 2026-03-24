// Venta.ts
import type { DetalleVenta } from "./DetalleVenta";

export interface Venta {
    id?: number;
    fechaHora: string;
    total: number;
    detalles?: DetalleVenta[];
}

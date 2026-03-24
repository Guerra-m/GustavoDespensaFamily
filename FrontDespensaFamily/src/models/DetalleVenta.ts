// DetalleVenta.ts
import type { Producto } from "./Producto";
import type { Venta } from "./Venta";

export interface DetalleVenta {
    id?: number;
    cantidad: number;
    precioUnitario: number;
    subtotal: number;
    venta?: Venta;       // solo tipo, no valor
    producto: Producto;
}

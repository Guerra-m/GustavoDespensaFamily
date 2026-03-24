import type { Producto } from "./Producto";
import type { Compra } from "./Compra";

export interface DetalleCompra {
    id?: number;
    cantidad: number;
    precioCosto: number;
    subtotal: number;
    compra?: Compra;
    producto: Producto;
}

import type { DetalleCompraResponse } from "./DetalleCompraResponse";
import type { MetodoPago } from "./MetodoPago";

export interface CompraDetalleResponse {
    id: number;
    fechaHora: string;
    total: number; // congelado en el servidor al registrar (ver Compra.java)
    pagada: boolean;
    metodoPago: MetodoPago;
    proveedor: string;
    detalles: DetalleCompraResponse[];
}

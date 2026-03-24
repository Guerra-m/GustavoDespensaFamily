import type { DetalleCompraResponse } from "./DetalleCompraResponse";

export interface CompraDetalleResponse {
    id: number;
    fechaHora: string;
    proveedor: string;
    detalles: DetalleCompraResponse[];
}

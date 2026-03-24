export interface CompraRequest {
  proveedor: string;
  detalles: DetalleCompraRequest[];
}

export interface DetalleCompraRequest {
  productoId: number;
  cantidad: number;
  precioCosto: number;
}

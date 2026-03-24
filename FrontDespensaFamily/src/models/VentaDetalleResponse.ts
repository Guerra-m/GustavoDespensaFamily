export interface DetalleVentaResponse {
  productoNombre: string;
  cantidad: number;
  precio: number;
  subtotal: number;
}

export interface VentaDetalleResponse {
  detalles: DetalleVentaResponse[];
}

/**
 * Respuesta de DELETE /compras/{id} — ver CompraService.anularCompra en el
 * backend. DF-BUG-005: nombres de los productos cuyo precio NO se restauró
 * porque cambió después de esta compra (edición manual, u otra compra más
 * nueva del mismo producto).
 */
export interface AnularCompraResponse {
  productosConPrecioConservado: string[];
}

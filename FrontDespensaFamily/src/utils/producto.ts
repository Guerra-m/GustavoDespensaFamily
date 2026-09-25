// utils/producto.ts
// Helpers de unidad/fracción de un Producto, compartidos por Ventas, Productos
// y Compras (Fase 3, DF-DEBT-002: estaban copiados y pegados en las 3 páginas).
import type { Producto } from "../models/Producto";

export function esProductoPesable(producto: Producto): boolean {
  return producto.tipoCantidad === "PESO";
}

export function unidadProducto(producto: Producto): "u" | "kg" {
  return producto.unidadMedida || (esProductoPesable(producto) ? "kg" : "u");
}

export function fraccionMinima(producto: Producto): number {
  if (producto.fraccionMinima && producto.fraccionMinima > 0) return producto.fraccionMinima;
  return esProductoPesable(producto) ? 0.001 : 1;
}

/** null si el producto no se vende en paquetes de 2+ unidades (o es pesable). */
export function desglosePaquete(
  cantidad: number,
  producto?: Producto
): { paquetes: number; sueltas: number } | null {
  const upp = producto?.unidadesPorPaquete;
  if (!producto || !upp || upp < 2 || esProductoPesable(producto)) return null;
  return { paquetes: Math.floor(cantidad / upp), sueltas: cantidad % upp };
}

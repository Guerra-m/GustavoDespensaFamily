// utils/producto.ts
// Helpers de unidad/fracción de un Producto, compartidos por Ventas, Productos
// y Compras (Fase 3, DF-DEBT-002: estaban copiados y pegados en las 3 páginas).
import type { Producto } from "../models/Producto";
import { formatearCantidad } from "./format";

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

/** "3 u" / "1,5 kg" -- sin producto, solo el número (DF-UX-003).
 * No es {@link formatearCantidadConUnidad} de utils/format.ts -- esa toma
 * la unidad como string suelto; esta la deduce del Producto. */
export function formatearCantidadDeProducto(cantidad: number, producto?: Producto): string {
  if (!producto) return formatearCantidad(cantidad);
  return `${formatearCantidad(cantidad)} ${unidadProducto(producto)}`;
}

/** Igual a {@link formatearCantidadDeProducto}, pero agrega el desglose en
 * paquetes cuando el producto se vende así (ver {@link desglosePaquete}). */
export function formatearCantidadConPaquete(cantidad: number, producto?: Producto): string {
  const base = formatearCantidadDeProducto(cantidad, producto);
  const desglose = desglosePaquete(cantidad, producto);
  if (!desglose) return base;
  return `${base} (${desglose.paquetes} paq + ${desglose.sueltas} u)`;
}

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

/** Texto compacto del desglose en paquetes (ej. "1 paq + 2 u", "2 paq"), o null si no llega a 1 paquete entero. */
export function textoDesglosePaquete(cantidad: number, producto?: Producto): string | null {
  const desglose = desglosePaquete(cantidad, producto);
  if (!desglose || desglose.paquetes === 0) return null;
  if (desglose.sueltas === 0) return `${desglose.paquetes} paq`;
  return `${desglose.paquetes} paq + ${desglose.sueltas} u`;
}

/** Igual a {@link formatearCantidadDeProducto}, pero agrega el desglose en
 * paquetes cuando el producto tiene 1 o más paquetes enteros (ver {@link desglosePaquete}). */
export function formatearCantidadConPaquete(cantidad: number, producto?: Producto): string {
  const base = formatearCantidadDeProducto(cantidad, producto);
  const texto = textoDesglosePaquete(cantidad, producto);
  if (!texto) return base;
  return `${base} (${texto})`;
}

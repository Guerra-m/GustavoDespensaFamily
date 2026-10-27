// utils/filtroProductos.ts
// Filtro de la grilla del POS (Ventas): texto libre (nombre o código de
// barras) combinado con la categoría activa. Extraído de renderProductosGrid
// (Fase 3, DF-DEBT-002/DF-DEBT-004) para poder testearlo sin el DOM.
import type { Producto } from "../models/Producto";

export interface FiltroProductos {
  texto: string;
  categoriaId: number | null;
}

export function filtrarProductos(productos: Producto[], filtro: FiltroProductos): Producto[] {
  const textoFiltro = filtro.texto.toLowerCase().trim();

  let resultado = textoFiltro
    ? productos.filter(p =>
        p.nombre.toLowerCase().includes(textoFiltro) ||
        p.codigoBarras?.toLowerCase().includes(textoFiltro)
      )
    : [...productos];

  if (filtro.categoriaId !== null) {
    resultado = resultado.filter(p => p.categoriaId === filtro.categoriaId);
  }

  return resultado;
}

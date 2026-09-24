export type AlcanceDescuento = "PRODUCTO" | "CATEGORIA";

export interface Descuento {
  id?: number;
  alcance: AlcanceDescuento;
  productoId?: number;
  productoNombre?: string;
  categoriaId?: number;
  categoriaNombre?: string;
  porcentaje: number;
  activo: boolean;
}

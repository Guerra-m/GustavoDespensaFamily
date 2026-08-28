export interface Promocion {
  id?: number;
  productoId: number;
  productoNombre?: string;
  cantidadLleva: number;
  cantidadPaga: number;
  activo: boolean;
}

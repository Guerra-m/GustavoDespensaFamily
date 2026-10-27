// ---------------- Interfaces ----------------
export interface ProductoBajoStock {
  id: number;
  nombre: string;
  stock: number;
}

export interface Movimiento {
  fecha: string;         // convertimos LocalDate a string
  tipo: "COMPRA" | "VENTA";
  productoId: number;
  productoNombre: string;
  cantidad: number;
  precio: number;
  subtotal: number;
  operacionId: number;   // id de compra o venta
}

export interface ProductoMasVendido {
  productoId: number;
  nombre: string;
  cantidadVendida: number;
}

export interface ResumenVentas {
  totalVentas: number;
  cantidadVentas: number;
  ganancia: number;
}

export interface VentasPorCategoria {
  categoriaId: number | null;
  categoriaNombre: string;
  totalVentas: number;
  cantidadVendida: number;
}

export interface GananciaPorFecha {
  fecha: string;
  totalVentas: number;
  ganancia: number;
}

/** Fase 8/9: rentabilidad, separada del flujo de caja. */
export interface Rentabilidad {
  ventasTotales: number;
  costoMercaderiaVendida: number;
  gananciaBruta: number;
  margenPorcentaje: number;
  /** Reposición de stock — no es un costo del período. */
  comprasTotales: number;
  /** Solo categoría GASTO: RETIRO y APORTE son movimientos de capital. */
  gastosTotales: number;
  /** false = nadie cargó gastos en el período; el resultado neto no es un dato confiable. */
  gastosCargados: boolean;
  resultadoNeto: number;
}

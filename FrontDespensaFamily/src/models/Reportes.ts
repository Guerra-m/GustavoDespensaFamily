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

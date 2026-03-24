import type { ProductoBajoStock, Movimiento, ProductoMasVendido, ResumenVentas } from "../models/Reportes";

const BASE_URL = `${import.meta.env.VITE_API_URL}/reportes`;
// Productos bajo stock
export async function getProductosBajoStock(): Promise<ProductoBajoStock[]> {
  const res = await fetch(`${BASE_URL}/productos-bajo-stock`);
  if (!res.ok) throw new Error("Error al obtener productos bajo stock");
  return res.json();
}

// Productos más vendidos
export async function getProductosMasVendidos(desde?: string, hasta?: string): Promise<ProductoMasVendido[]> {
  const params = new URLSearchParams();
  if (desde && desde.trim() !== "") params.append("desde", desde);
  if (hasta && hasta.trim() !== "") params.append("hasta", hasta);

  const url = params.toString()
    ? `${BASE_URL}/productos-mas-vendidos?${params.toString()}`
    : `${BASE_URL}/productos-mas-vendidos`;

  const res = await fetch(url);
  if (!res.ok) throw new Error("Error al obtener productos más vendidos");
  return res.json();
}

// Movimientos por fecha
export async function getMovimientos(desde?: string, hasta?: string): Promise<Movimiento[]> {
  const params = new URLSearchParams();
  if (desde && desde.trim() !== "") params.append("desde", desde);
  if (hasta && hasta.trim() !== "") params.append("hasta", hasta);

  const url = params.toString() ? `${BASE_URL}/movimientos?${params.toString()}` : `${BASE_URL}/movimientos`;
  const res = await fetch(url);
  if (!res.ok) throw new Error("Error al obtener movimientos");
  return res.json();
}

// Resumen diario/semanal/mensual
export async function getResumenVentas(periodo: "dia" | "semana" | "mes"): Promise<ResumenVentas> {
  const res = await fetch(`${BASE_URL}/resumen?periodo=${periodo}`);
  if (!res.ok) throw new Error("Error al obtener resumen de ventas");
  return res.json();
}

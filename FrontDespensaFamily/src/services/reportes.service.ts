import type { ProductoBajoStock, Movimiento, ProductoMasVendido, ResumenVentas, VentasPorCategoria, GananciaPorFecha, Rentabilidad } from "../models/Reportes";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/reportes`;

export async function getProductosBajoStock(): Promise<ProductoBajoStock[]> {
  const res = await fetch(`${BASE_URL}/productos-bajo-stock`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener productos bajo stock");
  return res.json();
}

export async function getProductosMasVendidos(desde?: string, hasta?: string): Promise<ProductoMasVendido[]> {
  const params = new URLSearchParams();
  if (desde && desde.trim() !== "") params.append("desde", desde);
  if (hasta && hasta.trim() !== "") params.append("hasta", hasta);

  const url = params.toString()
    ? `${BASE_URL}/productos-mas-vendidos?${params.toString()}`
    : `${BASE_URL}/productos-mas-vendidos`;

  const res = await fetch(url, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener productos más vendidos");
  return res.json();
}

export async function getMovimientos(desde?: string, hasta?: string, tipo?: "COMPRA" | "VENTA"): Promise<Movimiento[]> {
  const params = new URLSearchParams();
  if (desde && desde.trim() !== "") params.append("desde", desde);
  if (hasta && hasta.trim() !== "") params.append("hasta", hasta);
  if (tipo) params.append("tipo", tipo);

  const url = params.toString() ? `${BASE_URL}/movimientos?${params.toString()}` : `${BASE_URL}/movimientos`;
  const res = await fetch(url, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener movimientos");
  return res.json();
}

export async function getResumenVentas(periodo: "dia" | "semana" | "mes"): Promise<ResumenVentas> {
  const res = await fetch(`${BASE_URL}/resumen?periodo=${periodo}`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener resumen de ventas");
  return res.json();
}

export async function getVentasPorCategoria(desde: string, hasta: string): Promise<VentasPorCategoria[]> {
  const params = new URLSearchParams({ desde, hasta });
  const res = await fetch(`${BASE_URL}/ventas-por-categoria?${params.toString()}`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener ventas por categoría");
  return res.json();
}

export async function getGananciaPorFecha(desde: string, hasta: string): Promise<GananciaPorFecha[]> {
  const params = new URLSearchParams({ desde, hasta });
  const res = await fetch(`${BASE_URL}/ganancia?${params.toString()}`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener ganancia en el tiempo");
  return res.json();
}

export async function getRentabilidad(desde: string, hasta: string): Promise<Rentabilidad> {
  const params = new URLSearchParams({ desde, hasta });
  const res = await fetch(`${BASE_URL}/rentabilidad?${params.toString()}`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener la rentabilidad");
  return res.json();
}

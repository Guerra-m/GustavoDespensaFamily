import type { VentaDetalleResponse } from "../models/VentaDetalleResponse";
import type { Venta } from "../models/Venta";
import type { VentaRequest } from "../models/VentaRequest";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/ventas`;

export async function getVentas(): Promise<Venta[]> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener ventas");
  return res.json();
}

export async function createVenta(venta: VentaRequest): Promise<Venta> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(venta)
  });
  if (!res.ok) {
    // El backend devuelve { error: "..." } (ver GlobalExceptionHandler),
    // con 409 específicamente cuando otra venta/compra tocó el mismo
    // producto al mismo tiempo (conflicto de stock, no un bug).
    const body = await res.json().catch(() => null);
    const mensaje = body?.error || "Error al registrar venta";
    const error = new Error(mensaje) as Error & { status?: number };
    error.status = res.status;
    throw error;
  }
  return res.json();
}

// Tipado en vez de Promise<any>: los dos lugares que lo consumen recorren
// `detalles`, y con `any` el tipo del renglón se perdía (los parámetros de
// esos forEach quedaban implícitamente `any`).
export async function getDetalleVenta(ventaId: number): Promise<VentaDetalleResponse> {
  const res = await fetch(`${import.meta.env.VITE_API_URL}/ventas/${ventaId}/detalle`, {
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("Error al obtener detalle de la venta");
  return res.json();
}

export async function anularVenta(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("Error al anular la venta");
}

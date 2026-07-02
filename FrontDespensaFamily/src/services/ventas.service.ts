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
  if (!res.ok) throw new Error("Error al registrar venta");
  return res.json();
}

export async function getDetalleVenta(ventaId: number): Promise<any> {
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

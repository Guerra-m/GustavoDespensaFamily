import type { Compra } from "../models/Compra";
import type { CompraDetalleResponse } from "../models/CompraDetalleResponse";
import type { CompraRequest } from "../models/CompraRequest";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/compras`;

export async function getCompras(): Promise<Compra[]> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener compras");
  return res.json();
}

export async function createCompra(compra: CompraRequest): Promise<Compra> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(compra),
  });
  if (!res.ok) throw new Error("Error al registrar compra");
  return res.json();
}

export async function getCompraById(id: number): Promise<Compra> {
  const res = await fetch(`${BASE_URL}/${id}`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener compra");
  return res.json();
}

export async function getCompraDetalle(id: number): Promise<CompraDetalleResponse> {
  const res = await fetch(`${BASE_URL}/${id}/detalle`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener detalle de compra");
  return res.json();
}

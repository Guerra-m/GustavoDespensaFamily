import type { Compra } from "../models/Compra";
import type { CompraDetalleResponse } from "../models/CompraDetalleResponse";
import type { CompraRequest } from "../models/CompraRequest";

const BASE_URL = `${import.meta.env.VITE_API_URL}/compras`
// Traer compras
export async function getCompras(): Promise<Compra[]> {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener compras");
  return res.json();
}

// Registrar compra
export async function createCompra(compra: CompraRequest): Promise<Compra> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(compra),
  });
  if (!res.ok) throw new Error("Error al registrar compra");
  return res.json();
}

// Traer compra por id
export async function getCompraById(id: number): Promise<Compra> {
  const res = await fetch(`${BASE_URL}/${id}`);
  if (!res.ok) throw new Error("Error al obtener compra");
  return res.json();
}

// Traer compra con detalle
export async function getCompraDetalle(id: number): Promise<CompraDetalleResponse> {
    const res = await fetch(`${BASE_URL}/${id}/detalle`);
    if (!res.ok) throw new Error("Error al obtener detalle de compra");
    return res.json();
}

import type { Compra } from "../models/Compra";
import type { CompraDetalleResponse } from "../models/CompraDetalleResponse";
import type { CompraRequest, DetalleCompraRequest } from "../models/CompraRequest";
import type { CompraCotizacion } from "../models/CompraCotizacion";
import type { AnularCompraResponse } from "../models/AnularCompraResponse";
import { fetchApi } from "./api";

const BASE_URL = `${import.meta.env.VITE_API_URL}/compras`;

export async function getCompras(): Promise<Compra[]> {
  const res = await fetchApi(BASE_URL, {}, "No se pudieron obtener las compras");
  return res.json();
}

export async function createCompra(compra: CompraRequest): Promise<Compra> {
  const res = await fetchApi(
    BASE_URL,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(compra)
    },
    "No se pudo registrar la compra"
  );
  return res.json();
}

export async function getCompraById(id: number): Promise<Compra> {
  const res = await fetchApi(`${BASE_URL}/${id}`, {}, "No se pudo obtener la compra");
  return res.json();
}

export async function getCompraDetalle(id: number): Promise<CompraDetalleResponse> {
  const res = await fetchApi(`${BASE_URL}/${id}/detalle`, {}, "No se pudo obtener el detalle de la compra");
  return res.json();
}

/**
 * DF-RISK-001: qué precio de venta quedaría por línea si se confirmara esta
 * compra ahora mismo, sin registrar nada. Comparte la fórmula con el backend
 * (ver CompraService.previsualizar) — no se duplica la cuenta acá.
 */
export async function previsualizarCompra(detalles: DetalleCompraRequest[]): Promise<CompraCotizacion> {
  const res = await fetchApi(
    `${BASE_URL}/previsualizar`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ detalles })
    },
    "No se pudo previsualizar la compra"
  );
  return res.json();
}

/**
 * Deshace una compra: devuelve el stock que sumó y restaura el precio de costo
 * que el producto tenía antes. Solo ADMIN.
 *
 * Falla a propósito si ya se vendió parte de lo que entró con esa compra —
 * restar el stock igual lo dejaría en negativo.
 */
export async function anularCompra(id: number): Promise<AnularCompraResponse> {
  const res = await fetchApi(`${BASE_URL}/${id}`, { method: "DELETE" }, "No se pudo anular la compra");
  // DF-BUG-005: si el body viene vacío (204, o una respuesta vieja en
  // caché) no hay nada que avisar, no un error.
  return await res.json().catch(() => ({ productosConPrecioConservado: [] }));
}

/** Marca como pagada una compra que había quedado a cuenta (Fase 11). */
export async function marcarPagada(id: number): Promise<Compra> {
  const res = await fetchApi(
    `${BASE_URL}/${id}/pagar`,
    { method: "POST" },
    "No se pudo marcar la compra como pagada"
  );
  return res.json();
}

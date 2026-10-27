import type { ArqueoRequest, ArqueoResultado } from "../models/Arqueo";
import type { EstadoCaja } from "../models/EstadoCaja";
import type { MovimientoCaja } from "../models/MovimientoCaja";
import type { RegistrarMovimientoRequest } from "../models/RegistrarMovimientoRequest";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/caja`;

async function manejarError(res: Response, mensajePorDefecto: string): Promise<never> {
  const cuerpo = await res.json().catch(() => null);
  throw new Error(cuerpo?.error ?? mensajePorDefecto);
}

export async function getEstadoCaja(): Promise<EstadoCaja> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) return manejarError(res, "No se pudo obtener el estado de caja");
  return res.json();
}

export async function getUltimosMovimientos(): Promise<MovimientoCaja[]> {
  const res = await fetch(`${BASE_URL}/ultimos`, { headers: getAuthHeaders() });
  if (!res.ok) return manejarError(res, "No se pudieron obtener los movimientos");
  return res.json();
}

export async function getMovimientos(desde: string, hasta: string): Promise<MovimientoCaja[]> {
  const params = new URLSearchParams({ desde, hasta });
  const res = await fetch(`${BASE_URL}/movimientos?${params}`, { headers: getAuthHeaders() });
  if (!res.ok) return manejarError(res, "No se pudo obtener el historial de caja");
  return res.json();
}

export async function registrarMovimiento(
  request: RegistrarMovimientoRequest
): Promise<MovimientoCaja> {
  const res = await fetch(`${BASE_URL}/movimientos`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(request)
  });
  if (!res.ok) return manejarError(res, "No se pudo registrar el movimiento");
  return res.json();
}

export async function revertirMovimiento(id: number): Promise<MovimientoCaja> {
  const res = await fetch(`${BASE_URL}/movimientos/${id}/revertir`, {
    method: "POST",
    headers: getAuthHeaders()
  });
  if (!res.ok) return manejarError(res, "No se pudo revertir el movimiento");
  return res.json();
}

/** Cuenta el cajón y ajusta la diferencia contra lo esperado (Fase 6). */
export async function registrarArqueo(request: ArqueoRequest): Promise<ArqueoResultado> {
  const res = await fetch(`${BASE_URL}/arqueo`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(request)
  });
  if (!res.ok) return manejarError(res, "No se pudo registrar el arqueo");
  return res.json();
}

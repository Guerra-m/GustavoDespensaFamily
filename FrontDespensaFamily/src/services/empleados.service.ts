import type { CrearEmpleadoRequest } from "../models/CrearEmpleadoRequest";
import type { DiaTrabajado } from "../models/DiaTrabajado";
import type { Empleado } from "../models/Empleado";
import type { MovimientoCaja } from "../models/MovimientoCaja";
import type { PagarEmpleadoRequest } from "../models/PagarEmpleadoRequest";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/empleados`;

async function manejarError(res: Response, mensajePorDefecto: string): Promise<never> {
  const cuerpo = await res.json().catch(() => null);
  throw new Error(cuerpo?.error ?? mensajePorDefecto);
}

export async function getEmpleados(): Promise<Empleado[]> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) return manejarError(res, "No se pudieron obtener los empleados");
  return res.json();
}

export async function crearEmpleado(request: CrearEmpleadoRequest): Promise<Empleado> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(request)
  });
  if (!res.ok) return manejarError(res, "No se pudo crear el empleado");
  return res.json();
}

export async function desactivarEmpleado(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, { method: "DELETE", headers: getAuthHeaders() });
  if (!res.ok) return manejarError(res, "No se pudo desactivar el empleado");
}

export async function getDiasTrabajados(desde: string, hasta: string): Promise<DiaTrabajado[]> {
  const params = new URLSearchParams({ desde, hasta });
  const res = await fetch(`${BASE_URL}/dias?${params}`, { headers: getAuthHeaders() });
  if (!res.ok) return manejarError(res, "No se pudieron obtener los días trabajados");
  return res.json();
}

export async function marcarDia(empleadoId: number, fecha: string): Promise<void> {
  const res = await fetch(`${BASE_URL}/${empleadoId}/dias/${fecha}`, {
    method: "POST",
    headers: getAuthHeaders()
  });
  if (!res.ok) return manejarError(res, "No se pudo marcar el día");
}

export async function desmarcarDia(empleadoId: number, fecha: string): Promise<void> {
  const res = await fetch(`${BASE_URL}/${empleadoId}/dias/${fecha}`, {
    method: "DELETE",
    headers: getAuthHeaders()
  });
  if (!res.ok) return manejarError(res, "No se pudo desmarcar el día");
}

export async function pagarEmpleado(id: number, request: PagarEmpleadoRequest): Promise<MovimientoCaja> {
  const res = await fetch(`${BASE_URL}/${id}/pagar`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(request)
  });
  if (!res.ok) return manejarError(res, "No se pudo registrar el pago");
  return res.json();
}

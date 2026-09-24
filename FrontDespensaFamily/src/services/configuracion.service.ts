import type { Configuracion } from "../models/Configuracion";
import type { EstadoIa } from "../models/EstadoIa";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/configuracion`;

export async function getConfiguracion(): Promise<Configuracion> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al cargar la configuración");
  return res.json();
}

export async function actualizarConfiguracion(cambios: Partial<Configuracion>): Promise<Configuracion> {
  const res = await fetch(BASE_URL, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(cambios)
  });
  if (!res.ok) {
    const cuerpo = await res.json().catch(() => null);
    throw new Error(cuerpo?.error || "Error al guardar la configuración");
  }
  return res.json();
}

export async function aplicarGananciaGlobal(porcentaje: number): Promise<{ productosActualizados: number }> {
  const res = await fetch(`${BASE_URL}/aplicar-ganancia-global`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify({ porcentaje })
  });
  if (!res.ok) {
    const cuerpo = await res.json().catch(() => null);
    throw new Error(cuerpo?.error || "Error al aplicar la ganancia global");
  }
  return res.json();
}

// Asistente de IA (Gemini): la clave nunca vuelve del backend, ni siquiera
// enmascarada (SEC-01/SEC-05) — solo si esta configurada y de donde sale.
export async function getEstadoIa(): Promise<EstadoIa> {
  const res = await fetch(`${BASE_URL}/ia`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al consultar el estado del asistente de IA");
  return res.json();
}

export async function guardarClaveIa(apiKey: string): Promise<EstadoIa> {
  const res = await fetch(`${BASE_URL}/ia`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify({ apiKey })
  });
  if (!res.ok) {
    const cuerpo = await res.json().catch(() => null);
    throw new Error(cuerpo?.error || "Error al guardar la clave del asistente de IA");
  }
  return res.json();
}

export async function quitarClaveIa(): Promise<EstadoIa> {
  const res = await fetch(`${BASE_URL}/ia`, {
    method: "DELETE",
    headers: getAuthHeaders()
  });
  if (!res.ok) {
    const cuerpo = await res.json().catch(() => null);
    throw new Error(cuerpo?.error || "Error al quitar la clave del asistente de IA");
  }
  return res.json();
}

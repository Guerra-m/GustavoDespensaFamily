import type { Configuracion } from "../models/Configuracion";
import type { EstadoIa } from "../models/EstadoIa";
import { fetchApi } from "./api";

const BASE_URL = `${import.meta.env.VITE_API_URL}/configuracion`;

export async function getConfiguracion(): Promise<Configuracion> {
  const res = await fetchApi(BASE_URL, {}, "No se pudo cargar la configuración");
  return res.json();
}

export async function actualizarConfiguracion(cambios: Partial<Configuracion>): Promise<Configuracion> {
  const res = await fetchApi(
    BASE_URL,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(cambios)
    },
    "No se pudo guardar la configuración"
  );
  return res.json();
}

export async function aplicarGananciaGlobal(porcentaje: number): Promise<{ productosActualizados: number }> {
  const res = await fetchApi(
    `${BASE_URL}/aplicar-ganancia-global`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ porcentaje })
    },
    "No se pudo aplicar la ganancia global"
  );
  return res.json();
}

// Asistente de IA (Gemini): la clave nunca vuelve del backend, ni siquiera
// enmascarada (SEC-01/SEC-05) — solo si esta configurada y de donde sale.
export async function getEstadoIa(): Promise<EstadoIa> {
  const res = await fetchApi(`${BASE_URL}/ia`, {}, "No se pudo consultar el estado del asistente de IA");
  return res.json();
}

export async function guardarClaveIa(apiKey: string): Promise<EstadoIa> {
  const res = await fetchApi(
    `${BASE_URL}/ia`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ apiKey })
    },
    "No se pudo guardar la clave del asistente de IA"
  );
  return res.json();
}

export async function quitarClaveIa(): Promise<EstadoIa> {
  const res = await fetchApi(`${BASE_URL}/ia`, { method: "DELETE" }, "No se pudo quitar la clave del asistente de IA");
  return res.json();
}

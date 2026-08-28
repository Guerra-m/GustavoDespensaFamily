import type { Promocion } from "../models/Promocion";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/promociones`;

export async function getPromociones(): Promise<Promocion[]> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al cargar promociones");
  return res.json();
}

export async function createPromocion(promocion: Omit<Promocion, "id">): Promise<Promocion> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(promocion)
  });
  if (!res.ok) {
    const texto = await res.text();
    throw new Error(texto || "Error al crear promoción");
  }
  return res.json();
}

export async function updatePromocion(id: number, promocion: Promocion): Promise<Promocion> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(promocion)
  });
  if (!res.ok) {
    const texto = await res.text();
    throw new Error(texto || "Error al actualizar promoción");
  }
  return res.json();
}

export async function deletePromocion(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("Error al eliminar promoción");
}

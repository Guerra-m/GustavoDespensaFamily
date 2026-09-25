import type { Promocion } from "../models/Promocion";
import { fetchApi } from "./api";

const BASE_URL = `${import.meta.env.VITE_API_URL}/promociones`;

export async function getPromociones(): Promise<Promocion[]> {
  const res = await fetchApi(BASE_URL, {}, "No se pudieron cargar las promociones");
  return res.json();
}

export async function createPromocion(promocion: Omit<Promocion, "id">): Promise<Promocion> {
  const res = await fetchApi(
    BASE_URL,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(promocion)
    },
    "No se pudo crear la promoción"
  );
  return res.json();
}

export async function updatePromocion(id: number, promocion: Promocion): Promise<Promocion> {
  const res = await fetchApi(
    `${BASE_URL}/${id}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(promocion)
    },
    "No se pudo actualizar la promoción"
  );
  return res.json();
}

export async function deletePromocion(id: number): Promise<void> {
  await fetchApi(`${BASE_URL}/${id}`, { method: "DELETE" }, "No se pudo eliminar la promoción");
}

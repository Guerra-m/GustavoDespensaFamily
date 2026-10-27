import type { Descuento } from "../models/Descuento";
import { fetchApi } from "./api";

const BASE_URL = `${import.meta.env.VITE_API_URL}/descuentos`;

export async function getDescuentos(): Promise<Descuento[]> {
  const res = await fetchApi(BASE_URL, {}, "No se pudieron cargar los descuentos");
  return res.json();
}

export async function createDescuento(descuento: Omit<Descuento, "id">): Promise<Descuento> {
  const res = await fetchApi(
    BASE_URL,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(descuento)
    },
    "No se pudo crear el descuento"
  );
  return res.json();
}

export async function updateDescuento(id: number, descuento: Descuento): Promise<Descuento> {
  const res = await fetchApi(
    `${BASE_URL}/${id}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(descuento)
    },
    "No se pudo actualizar el descuento"
  );
  return res.json();
}

export async function deleteDescuento(id: number): Promise<void> {
  await fetchApi(`${BASE_URL}/${id}`, { method: "DELETE" }, "No se pudo eliminar el descuento");
}

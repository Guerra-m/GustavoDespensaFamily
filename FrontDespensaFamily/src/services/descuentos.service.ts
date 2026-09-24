import type { Descuento } from "../models/Descuento";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/descuentos`;

export async function getDescuentos(): Promise<Descuento[]> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al cargar descuentos");
  return res.json();
}

export async function createDescuento(descuento: Omit<Descuento, "id">): Promise<Descuento> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(descuento)
  });
  if (!res.ok) {
    const texto = await res.text();
    throw new Error(texto || "Error al crear descuento");
  }
  return res.json();
}

export async function updateDescuento(id: number, descuento: Descuento): Promise<Descuento> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(descuento)
  });
  if (!res.ok) {
    const texto = await res.text();
    throw new Error(texto || "Error al actualizar descuento");
  }
  return res.json();
}

export async function deleteDescuento(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("Error al eliminar descuento");
}

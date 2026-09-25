import type { Categoria } from "../models/Categoria";
import { fetchApi } from "./api";

const BASE_URL = `${import.meta.env.VITE_API_URL}/categorias`;

export async function getCategorias(): Promise<Categoria[]> {
  const res = await fetchApi(BASE_URL, {}, "No se pudieron cargar las categorías");
  return res.json();
}

export async function createCategoria(categoria: Omit<Categoria, "id">): Promise<Categoria> {
  const res = await fetchApi(
    BASE_URL,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(categoria)
    },
    "No se pudo crear la categoría"
  );
  return res.json();
}

export async function updateCategoria(id: number, categoria: Categoria): Promise<Categoria> {
  const res = await fetchApi(
    `${BASE_URL}/${id}`,
    {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(categoria)
    },
    "No se pudo actualizar la categoría"
  );
  return res.json();
}

export async function deleteCategoria(id: number): Promise<void> {
  await fetchApi(`${BASE_URL}/${id}`, { method: "DELETE" }, "No se pudo eliminar la categoría");
}

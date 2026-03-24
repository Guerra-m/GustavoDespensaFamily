import type { Categoria } from "../models/Categoria";


const BASE_URL = `${import.meta.env.VITE_API_URL}/categorias`
// Traer todas las categorías
export async function getCategorias(): Promise<Categoria[]> {
    const res = await fetch(BASE_URL);
    if (!res.ok) throw new Error("Error al cargar categorías");
    return res.json();
}

// Crear categoría
export async function createCategoria(
  categoria: Omit<Categoria, "id">
): Promise<Categoria> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(categoria)
  });

  if (!res.ok) throw new Error("Error al crear categoría");
  return res.json();
}

// Actualizar categoría
export async function updateCategoria(id: number, categoria: Categoria): Promise<Categoria> {
    const res = await fetch(`${BASE_URL}/${id}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(categoria)
    });
    if (!res.ok) throw new Error("Error al actualizar categoría");
    return res.json();
}

// Eliminar categoría
export async function deleteCategoria(id: number): Promise<void> {
    const res = await fetch(`${BASE_URL}/${id}`, { method: "DELETE" });
    if (!res.ok) throw new Error("Error al eliminar categoría");
}

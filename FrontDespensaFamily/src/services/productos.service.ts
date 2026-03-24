import type { Producto } from "../models/Producto";

const BASE_URL = `${import.meta.env.VITE_API_URL}/productos`;

// ---------- TRAER TODOS ----------
export async function getProductos(): Promise<Producto[]> {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al cargar productos");
  return res.json();
}

// ---------- CREAR (FormData + imagen opcional) ----------
export async function createProducto(fd: FormData): Promise<Producto> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    body: fd // 🚫 NO headers
  });

  if (!res.ok) {
    const text = await res.text();
    console.error("Error detalle:", text);
    throw new Error("Error al crear producto");
  }
  return res.json();
}



// ---------- BUSCAR POR ID ----------
export async function getProductoById(id: number): Promise<Producto> {
  const res = await fetch(`${BASE_URL}/${id}`);
  if (!res.ok) throw new Error("Producto no encontrado");
  return res.json();
}

// ---------- EDITAR (FormData + imagen opcional) ----------
export async function updateProducto(id: number, fd: FormData): Promise<Producto> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    body: fd // 👈 SIN headers
  });

  if (!res.ok) throw new Error("Error al actualizar producto");
  return res.json();
}

// ---------- STOCK / PRECIO (JSON NORMAL) ----------
export async function updateStockPrecio(
  id: number,
  data: { stock: number; precio: number }
): Promise<Producto> {
  const res = await fetch(`${BASE_URL}/${id}/stock-precio`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(data)
  });

  if (!res.ok) throw new Error("Error al actualizar stock y precio");
  return res.json();
}

// ---------- BUSCAR ----------
export async function buscarProductos(params: {
  nombre?: string;
  codigoBarras?: string;
  categoriaId?: number;
}): Promise<Producto[]> {
  const query = new URLSearchParams();

  if (params.nombre) query.append("nombre", params.nombre);
  if (params.codigoBarras) query.append("codigoBarras", params.codigoBarras);
  if (params.categoriaId !== undefined)
    query.append("categoriaId", params.categoriaId.toString());

  const res = await fetch(`${BASE_URL}/buscar?${query.toString()}`);
  if (!res.ok) throw new Error("Error al buscar productos");
  return res.json();
}

// ---------- ELIMINAR ----------
export async function deleteProducto(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, { method: "DELETE" });
  if (!res.ok) throw new Error("Error al eliminar producto");
}

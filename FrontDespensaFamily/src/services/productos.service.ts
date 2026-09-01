import type { Producto } from "../models/Producto";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/productos`;

export async function getProductos(): Promise<Producto[]> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al cargar productos");
  return res.json();
}

export async function createProducto(fd: FormData): Promise<Producto> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: getAuthHeaders(), // sin Content-Type: FormData lo setea automáticamente
    body: fd
  });
  if (!res.ok) {
    const text = await res.text();
    console.error("Error detalle:", text);
    throw new Error("Error al crear producto");
  }
  return res.json();
}

export async function getProductoById(id: number): Promise<Producto> {
  const res = await fetch(`${BASE_URL}/${id}`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Producto no encontrado");
  return res.json();
}

export async function updateProducto(id: number, fd: FormData): Promise<Producto> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "PUT",
    headers: getAuthHeaders(),
    body: fd
  });
  if (!res.ok) throw new Error("Error al actualizar producto");
  return res.json();
}

export async function updateStockPrecio(
  id: number,
  data: { stock: number; precio: number }
): Promise<Producto> {
  // PATCH y no PUT: el backend expone PATCH /{id}/stock-precio. Con PUT esta
  // llamada nunca funcionó (el verbo no está mapeado, así que respondía 403).
  const res = await fetch(`${BASE_URL}/${id}/stock-precio`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify(data)
  });
  if (!res.ok) throw new Error("Error al actualizar stock y precio");
  return res.json();
}

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

  const res = await fetch(`${BASE_URL}/buscar?${query.toString()}`, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al buscar productos");
  return res.json();
}

export async function deleteProducto(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("Error al eliminar producto");
}

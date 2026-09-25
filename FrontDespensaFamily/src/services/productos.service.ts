import type { Producto } from "../models/Producto";
import { fetchApi } from "./api";

const BASE_URL = `${import.meta.env.VITE_API_URL}/productos`;

export async function getProductos(): Promise<Producto[]> {
  const res = await fetchApi(BASE_URL, {}, "No se pudieron cargar los productos");
  return res.json();
}

export async function createProducto(fd: FormData): Promise<Producto> {
  // Sin Content-Type: FormData lo setea automáticamente (con el boundary).
  const res = await fetchApi(BASE_URL, { method: "POST", body: fd }, "No se pudo crear el producto");
  return res.json();
}

export async function getProductoById(id: number): Promise<Producto> {
  const res = await fetchApi(`${BASE_URL}/${id}`, {}, "No se encontró el producto");
  return res.json();
}

export async function updateProducto(id: number, fd: FormData): Promise<Producto> {
  const res = await fetchApi(`${BASE_URL}/${id}`, { method: "PUT", body: fd }, "No se pudo actualizar el producto");
  return res.json();
}

export async function updateStockPrecio(
  id: number,
  data: { stock: number; precio: number }
): Promise<Producto> {
  // PATCH y no PUT: el backend expone PATCH /{id}/stock-precio. Con PUT esta
  // llamada nunca funcionó (el verbo no está mapeado, así que respondía 403).
  const res = await fetchApi(
    `${BASE_URL}/${id}/stock-precio`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data)
    },
    "No se pudo actualizar el stock y el precio"
  );
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

  const res = await fetchApi(`${BASE_URL}/buscar?${query.toString()}`, {}, "No se pudo buscar productos");
  return res.json();
}

export async function deleteProducto(id: number): Promise<void> {
  await fetchApi(`${BASE_URL}/${id}`, { method: "DELETE" }, "No se pudo eliminar el producto");
}

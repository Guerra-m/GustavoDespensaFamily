import type { ProductoExtraido } from "../models/ProductoExtraido";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/ia`;

export async function extraerProductos(texto: string, imagen?: File | null): Promise<ProductoExtraido[]> {
  const fd = new FormData();
  if (texto && texto.trim()) fd.append("texto", texto.trim());
  if (imagen) fd.append("imagen", imagen);

  const res = await fetch(`${BASE_URL}/productos/extraer`, {
    method: "POST",
    headers: getAuthHeaders(), // sin Content-Type: FormData lo setea solo
    body: fd
  });

  if (!res.ok) {
    const cuerpo = await res.json().catch(() => null);
    throw new Error(cuerpo?.error || "No se pudo analizar con el asistente de IA");
  }

  const data = await res.json();
  return data.productos as ProductoExtraido[];
}

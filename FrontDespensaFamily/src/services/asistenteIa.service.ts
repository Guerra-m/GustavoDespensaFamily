import type { ProductoExtraido } from "../models/ProductoExtraido";
import { fetchApi } from "./api";

const BASE_URL = `${import.meta.env.VITE_API_URL}/ia`;

export async function extraerProductos(texto: string, imagen?: File | null): Promise<ProductoExtraido[]> {
  const fd = new FormData();
  if (texto && texto.trim()) fd.append("texto", texto.trim());
  if (imagen) fd.append("imagen", imagen);

  // Sin Content-Type: FormData lo setea solo.
  const res = await fetchApi(
    `${BASE_URL}/productos/extraer`,
    { method: "POST", body: fd },
    "No se pudo analizar con el asistente de IA"
  );

  const data = await res.json();
  return data.productos as ProductoExtraido[];
}

// Lo que devuelve /ia/productos/extraer: un candidato a producto, todavía sin
// confirmar. Espejo del ProductoExtraidoDTO del backend.
export interface ProductoExtraido {
  nombre: string;
  marca?: string;
  presentacion?: string;
  unidad?: "u" | "kg";
  categoriaSugerida?: string;
  codigoBarras?: string;
  precio?: number | null;
  confianza?: "ALTA" | "MEDIA" | "BAJA";
}

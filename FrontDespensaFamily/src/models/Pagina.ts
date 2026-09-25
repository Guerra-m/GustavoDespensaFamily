/**
 * DF-PERF-001: lo que devuelve cualquier endpoint /pagina del backend
 * (PaginaDTO<T>). Un empleado sin PERM_CAJA/PERM_STOCK recibe la MISMA
 * forma, solo que ya filtrada del lado del servidor -- el frontend no
 * necesita saber nada de permisos para leer esto.
 */
export interface Pagina<T> {
  contenido: T[];
  totalElementos: number;
  totalPaginas: number;
  pagina: number;
  tamanio: number;
}

export interface Configuracion {
  umbralStockBajo: number;
  nombreNegocio: string;
  // Solo para quien ve costos (dueño o PERM_STOCK). DF-SEC-003.
  gananciaPorDefecto?: number;
}

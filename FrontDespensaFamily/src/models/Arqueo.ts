import type { MovimientoCaja } from "./MovimientoCaja";

export interface ArqueoRequest {
  efectivoContado: number;
  descripcion?: string;
}

export interface ArqueoResultado {
  efectivoEsperado: number;
  efectivoContado: number;
  diferencia: number;
  /** null si dio exacto: no hubo nada que ajustar. */
  movimiento: MovimientoCaja | null;
}

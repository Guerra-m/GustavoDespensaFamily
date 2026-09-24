import type { CategoriaMovimiento } from "./CategoriaMovimiento";
import type { MetodoPago } from "./MetodoPago";
import type { TipoMovimiento } from "./TipoMovimiento";

export interface MovimientoCaja {
  id: number;
  /** ISO con hora. */
  fechaHora: string;
  tipo: TipoMovimiento;
  categoria: CategoriaMovimiento;
  monto: number;
  metodoPago: MetodoPago;
  descripcion?: string | null;
  usuario?: string | null;
  ventaId?: number | null;
  compraId?: number | null;
  revierteA?: number | null;
  /** true si algún otro movimiento ya lo revirtió. */
  revertido: boolean;
}

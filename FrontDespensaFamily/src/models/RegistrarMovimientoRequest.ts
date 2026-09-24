import type { CategoriaMovimiento } from "./CategoriaMovimiento";
import type { MetodoPago } from "./MetodoPago";
import type { TipoMovimiento } from "./TipoMovimiento";

/** Carga manual: GASTO, RETIRO, APORTE o AJUSTE. VENTA/COMPRA las genera el
 * propio flujo de la venta o la compra, no se cargan por acá (ver CajaService). */
export interface RegistrarMovimientoRequest {
  tipo: TipoMovimiento;
  categoria: CategoriaMovimiento;
  monto: number;
  metodoPago: MetodoPago;
  descripcion: string;
}

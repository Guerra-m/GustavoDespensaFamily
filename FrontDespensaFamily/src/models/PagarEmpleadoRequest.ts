import type { MetodoPago } from "./MetodoPago";

/** Monto libre y sin período fijo, por decisión del dueño. */
export interface PagarEmpleadoRequest {
  monto: number;
  metodoPago: MetodoPago;
  descripcion?: string;
}

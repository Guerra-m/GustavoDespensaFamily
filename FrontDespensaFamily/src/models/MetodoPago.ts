/** Como se cobro. Separa el cajon fisico (EFECTIVO) del dinero electronico
 * (TARJETA, TRANSFERENCIA, OTRO) — ver CajaService del backend. */
export type MetodoPago = "EFECTIVO" | "TARJETA" | "TRANSFERENCIA" | "OTRO";

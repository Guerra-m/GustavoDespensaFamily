
import type { MetodoPago } from "./MetodoPago";

export interface Compra {
    id?: number;
    fechaHora: string; // ISO con hora
    total?: number; // congelado en el servidor al registrar (ver Compra.java)
    pagada?: boolean;
    metodoPago?: MetodoPago;
    estado?: "ACTIVA" | "ANULADA";
    usuario?: string | null;
    anuladaPor?: string | null;
    proveedor: string;
}

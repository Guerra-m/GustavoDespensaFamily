
export interface Compra {
    id?: number;
    fechaHora: string; // ISO con hora
    estado?: "ACTIVA" | "ANULADA";
    usuario?: string | null;
    anuladaPor?: string | null;
    proveedor: string;
}

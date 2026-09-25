

export interface Producto {
    id?: number;
    nombre: string;
    precioVenta: number;
    // Sin PERM_STOCK el servidor no los manda (DF-SEC-003).
    precioCosto?: number;
    porcentajeGanancia?: number;
    stock: number;
    tipoCantidad?: "UNIDAD" | "PESO";
    unidadMedida?: "u" | "kg";
    fraccionMinima?: number;
    unidadesPorPaquete?: number;
    codigoBarras?: string;
    proveedor?: string;
    categoriaId?: number; 
    imagenUrl?: string; 
}

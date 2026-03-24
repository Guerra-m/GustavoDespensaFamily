

export interface Producto {
    id?: number;
    nombre: string;
    precioVenta: number;
    precioCosto: number;
    porcentajeGanancia: number;
    stock: number;
    codigoBarras?: string;
    proveedor?: string;
    categoriaId?: number; 
    imagenUrl?: string; 
}

import type { Venta } from "../models/Venta";
import type { VentaRequest } from "../models/VentaRequest";

const BASE_URL = `${import.meta.env.VITE_API_URL}/ventas`;
// Traer ventas
export async function getVentas(): Promise<Venta[]> {
  const res = await fetch(BASE_URL);
  if (!res.ok) throw new Error("Error al obtener ventas");
  return res.json();
}

// Registrar venta
export async function createVenta(venta: VentaRequest): Promise<Venta> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(venta)
  });
  if (!res.ok) throw new Error("Error al registrar venta");
  return res.json();
}

export async function getDetalleVenta(ventaId: number): Promise<{productoNombre: string, cantidad: number, precio: number, subtotal: number}[]> {
  const res = await fetch(`http://localhost:8080/ventas/${ventaId}/detalle`);
  if (!res.ok) throw new Error("Error al obtener detalle de la venta");
  return res.json();
}
// Anular venta
export async function anularVenta(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE"
  });

  if (!res.ok) {
    throw new Error("Error al anular la venta");
  }
}

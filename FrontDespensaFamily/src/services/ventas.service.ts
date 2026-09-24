import type { VentaDetalleResponse } from "../models/VentaDetalleResponse";
import type { Venta } from "../models/Venta";
import type { VentaRequest } from "../models/VentaRequest";
import type { Cotizacion } from "../models/Cotizacion";
import type { DetalleVentaRequest } from "../models/DetalleVentaRequest";
import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/ventas`;

export async function getVentas(): Promise<Venta[]> {
  const res = await fetch(BASE_URL, { headers: getAuthHeaders() });
  if (!res.ok) throw new Error("Error al obtener ventas");
  return res.json();
}

// DF-BUG-003: idempotencyKey es opcional a propósito — sin ella esto se
// comporta exactamente igual que antes (ver VentaController). El caller
// (ventas.ts) manda un UUID por intento de confirmar para que un doble clic
// o un reintento por red no generen dos ventas.
export async function createVenta(venta: VentaRequest, idempotencyKey?: string): Promise<Venta> {
  const res = await fetch(BASE_URL, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      ...(idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {}),
      ...getAuthHeaders()
    },
    body: JSON.stringify(venta)
  });
  if (!res.ok) {
    // El backend devuelve { error: "..." } (ver GlobalExceptionHandler),
    // con 409 específicamente cuando otra venta/compra tocó el mismo
    // producto al mismo tiempo (conflicto de stock, no un bug).
    const body = await res.json().catch(() => null);
    const mensaje = body?.error || "Error al registrar venta";
    const error = new Error(mensaje) as Error & { status?: number };
    error.status = res.status;
    throw error;
  }
  return res.json();
}

// DF-BUG-001: lo que cobraría registrarVenta con este carrito ahora mismo,
// calculado en el servidor (VentaService.cotizar) — comparte el mismo método
// que la venta real, así que el total que ve el carrito es siempre el que
// después se cobra.
export async function cotizarVenta(detalles: DetalleVentaRequest[]): Promise<Cotizacion> {
  const res = await fetch(`${BASE_URL}/cotizar`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify({ detalles })
  });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    const mensaje = body?.error || "No se pudo calcular el total";
    throw new Error(mensaje);
  }
  return res.json();
}

// Tipado en vez de Promise<any>: los dos lugares que lo consumen recorren
// `detalles`, y con `any` el tipo del renglón se perdía (los parámetros de
// esos forEach quedaban implícitamente `any`).
export async function getDetalleVenta(ventaId: number): Promise<VentaDetalleResponse> {
  const res = await fetch(`${import.meta.env.VITE_API_URL}/ventas/${ventaId}/detalle`, {
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("Error al obtener detalle de la venta");
  return res.json();
}

export async function anularVenta(id: number): Promise<void> {
  const res = await fetch(`${BASE_URL}/${id}`, {
    method: "DELETE",
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("Error al anular la venta");
}

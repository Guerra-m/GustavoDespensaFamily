// Helpers para armar datos de prueba por API (nunca por el catálogo real, ver
// CLAUDE.md). Reciben un APIRequestContext -- en los specs se usa
// `context.request` (comparte cookies con el `page` de ese mismo
// BrowserContext), así que si ya se hizo login por la UI en `page`, estos
// pedidos ya viajan autenticados sin volver a loguearse.
import type { APIRequestContext } from "@playwright/test";
import { BASE_URL } from "./env.ts";

let contadorNombres = 0;

/** Nombre de producto único por corrida, para no chocar entre specs ni con corridas previas. */
export function nombreUnico(prefijo: string): string {
  contadorNombres += 1;
  return `${prefijo} E2E ${Date.now()}-${contadorNombres}`;
}

export interface ProductoCreado {
  id: number;
  nombre: string;
}

export async function crearProducto(
  request: APIRequestContext,
  datos: {
    nombre: string;
    precioVenta: number;
    stock: number;
    tipoCantidad?: "UNIDAD" | "PESO";
    unidadMedida?: string;
    fraccionMinima?: number;
  }
): Promise<ProductoCreado> {
  const dto = {
    nombre: datos.nombre,
    precioVenta: datos.precioVenta,
    precioCosto: 0,
    porcentajeGanancia: 0,
    stock: datos.stock,
    tipoCantidad: datos.tipoCantidad ?? "UNIDAD",
    unidadMedida: datos.unidadMedida,
    // Mapper.toEntity default es 1 si no se manda -- para un pesable real la
    // fracción mínima es el gramo (0,001), no el kilo entero.
    fraccionMinima: datos.fraccionMinima ?? (datos.tipoCantidad === "PESO" ? 0.001 : undefined),
  };

  const res = await request.post(`${BASE_URL}/productos`, {
    multipart: { producto: JSON.stringify(dto) },
  });
  if (!res.ok()) {
    throw new Error(`No se pudo crear el producto de prueba: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return { id: body.id, nombre: body.nombre };
}

export async function crearDescuentoPorProducto(
  request: APIRequestContext,
  productoId: number,
  porcentaje: number
): Promise<void> {
  const res = await request.post(`${BASE_URL}/descuentos`, {
    data: { alcance: "PRODUCTO", productoId, porcentaje, activo: true },
  });
  if (!res.ok()) {
    throw new Error(`No se pudo crear el descuento de prueba: ${res.status()} ${await res.text()}`);
  }
}

export interface EmpleadoCreado {
  id: number;
  username: string;
  password: string;
}

/** Empleado SIN PERM_CAJA ni PERM_STOCK -- el caso "básico" del flujo 5. */
export async function crearEmpleadoBasico(
  request: APIRequestContext,
  username: string
): Promise<EmpleadoCreado> {
  const password = "Empleado-E2E-2026!";
  const res = await request.post(`${BASE_URL}/admin/usuarios`, {
    data: {
      username,
      password,
      nombre: `Empleado ${username}`,
      rol: "EMPLEADO",
      puedeGestionarProductos: false,
      puedeVerCaja: false,
    },
  });
  if (!res.ok()) {
    throw new Error(`No se pudo crear el empleado de prueba: ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return { id: body.id, username, password };
}

export async function contarVentas(request: APIRequestContext): Promise<number> {
  const res = await request.get(`${BASE_URL}/ventas`);
  if (!res.ok()) {
    throw new Error(`No se pudo listar ventas: ${res.status()} ${await res.text()}`);
  }
  const ventas = await res.json();
  return ventas.length;
}

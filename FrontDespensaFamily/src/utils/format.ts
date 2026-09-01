/**
 * Formato de cantidades (stock, kilos, unidades).
 *
 * El backend guarda las cantidades como número de punto flotante, y restar
 * 1,538 de 5 no da 3,462 sino 3.4619999999999997. El backend ya redondea al
 * gramo al escribir y al leer; acá se redondea igual por dos motivos: los
 * productos que quedaron sucios antes de ese arreglo, y las cuentas que hace
 * la propia pantalla sin pasar por el servidor (por ejemplo descontar el
 * carrito del stock que ya está en memoria).
 *
 * Tres decimales = el gramo, que es la fracción mínima de un producto pesable.
 */
const DECIMALES = 3;

export function redondearCantidad(cantidad: number): number {
  if (!Number.isFinite(cantidad)) return cantidad;
  return Number(cantidad.toFixed(DECIMALES));
}

/**
 * Sin ceros al final: 3.462 → "3.462", 2 → "2" y no "2.000". El cartel de un
 * producto por unidad tiene que decir "2 u", no "2.000 u".
 */
export function formatearCantidad(cantidad: number): string {
  if (!Number.isFinite(cantidad)) return "0";
  return String(redondearCantidad(cantidad));
}

/**
 * El backend guarda `imagenUrl` como ruta relativa (ej. "/uploads/foto.jpg").
 * En el build empaquetado eso alcanza porque front y back comparten origen,
 * pero en desarrollo el front vive en el puerto de Vite (5173) y el back en
 * otro (8080) — una ruta relativa se resuelve contra Vite, no contra el
 * backend, y la imagen rompe. Acá se antepone VITE_API_URL solo cuando hace
 * falta (si ya es una URL absoluta, se deja igual).
 */
export function resolverImagenUrl(url?: string): string | undefined {
  if (!url) return undefined;
  if (/^https?:\/\//i.test(url)) return url;
  const base = import.meta.env.VITE_API_URL || "";
  return `${base}${url}`;
}

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

/**
 * Escapa texto antes de meterlo en HTML.
 *
 * Casi todas las pantallas arman sus filas y tarjetas con `innerHTML` y
 * plantillas: `<div>${prod.nombre}</div>`. El nombre de un producto lo escribe
 * una persona, así que puede traer HTML — y sin escapar, ese HTML se ejecuta.
 *
 * No es teórico ni hace falta un atacante externo: alcanza con que alguien con
 * acceso al alta de productos guarde un producto llamado
 * `<img src=x onerror="...">` para que ese código corra en la sesión de
 * cualquiera que abra la lista, incluido el dueño. Y como el token vive en
 * localStorage, eso es directamente robo de sesión.
 *
 * Se escapan también las comillas: muchos de estos valores caen dentro de un
 * atributo (`alt="${prod.nombre}"`), donde una comilla sola ya permite salirse
 * del atributo y agregar otro.
 */
export function esc(valor: unknown): string {
  if (valor === null || valor === undefined) return "";
  return String(valor)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/**
 * Fecha y hora para mostrar. El backend manda ISO ("2026-09-01T14:23:45")
 * desde que las ventas y compras guardan la hora y no solo el día; sin esto
 * la pantalla mostraba la T y los segundos crudos.
 */
export function formatearFechaHora(iso?: string | null): string {
  if (!iso) return "";
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return String(iso);
  return fecha.toLocaleString("es-AR", {
    day: "2-digit", month: "2-digit", year: "numeric",
    hour: "2-digit", minute: "2-digit"
  });
}

/** Solo el día, para columnas donde la hora no aporta. */
export function formatearFecha(iso?: string | null): string {
  if (!iso) return "";
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return String(iso);
  return fecha.toLocaleDateString("es-AR", {
    day: "2-digit", month: "2-digit", year: "numeric"
  });
}

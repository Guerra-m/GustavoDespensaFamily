import { getAuthHeaders } from "./auth.service";

const BASE_URL = import.meta.env.VITE_API_URL;

/**
 * Error de API ya traducido: `.message` es texto pensado para mostrarse tal
 * cual al usuario (castellano rioplatense, sin jerga técnica), no para loguear.
 */
export class ErrorApi extends Error {
  status?: number;
  constructor(mensaje: string, status?: number) {
    super(mensaje);
    this.name = "ErrorApi";
    this.status = status;
  }
}

const MENSAJE_SIN_CONEXION =
  "No hay conexión con el sistema. Revisá que DespensaFamily esté abierto y volvé a intentar.";

/**
 * DF-UX-002: traduce una respuesta HTTP que falló (!response.ok) a un mensaje
 * mostrable.
 *
 * - 403: el backend ya decidió que esta persona no puede hacer esto
 *   (SecurityConfig) — un solo texto para todos los 403 en vez de que cada
 *   pantalla que se olvide de chequear el rol muestre "No se pudo X" genérico.
 * - Resto: prioriza `detalle` sobre `error` — la Fase 1 dejó los 400 de
 *   validación como `{error:"Datos inválidos", detalle:"..."}`, y el detalle
 *   es el que de verdad ayuda a corregir el dato. Si el backend no mandó
 *   ninguno de los dos (por ejemplo un 500 con stacktrace de Hibernate en el
 *   body), se usa el mensaje por defecto de quien llama en vez de mostrar
 *   eso tal cual.
 */
async function traducirRespuesta(res: Response, mensajePorDefecto: string): Promise<ErrorApi> {
  if (res.status === 403) {
    return new ErrorApi("No tenés permiso para hacer esto.", 403);
  }
  const cuerpo = await res.json().catch(() => null);
  const mensaje = (cuerpo?.detalle as string) || (cuerpo?.error as string) || mensajePorDefecto;
  return new ErrorApi(mensaje, res.status);
}

/**
 * `fetch` envuelto para los services (todos menos ventas.service.ts, que
 * tiene sus propias reglas de carrito/stock, y auth.service.ts, que instala
 * el interceptor global de 401 sobre `window.fetch` — este helper corre
 * arriba de ese interceptor, no lo reemplaza).
 *
 * Traduce dos cosas que antes cada service armaba (o no armaba) por su
 * cuenta:
 *  1. Backend caído o sin red: `fetch` tira un `TypeError` genérico ("Failed
 *     to fetch") que no dice nada útil a quien vende — acá se convierte en
 *     el mensaje de conexión de DF-UX-002.
 *  2. HTTP no-ok: ver `traducirRespuesta`.
 */
export async function fetchApi(
  input: string,
  init: RequestInit = {},
  mensajePorDefecto = "Ocurrió un error. Volvé a intentar."
): Promise<Response> {
  let res: Response;
  try {
    res = await fetch(input, { ...init, headers: { ...getAuthHeaders(), ...(init.headers ?? {}) } });
  } catch {
    throw new ErrorApi(MENSAJE_SIN_CONEXION);
  }
  if (!res.ok) {
    throw await traducirRespuesta(res, mensajePorDefecto);
  }
  return res;
}

export async function getPerfil() {
  const res = await fetchApi(`${BASE_URL}/usuario/perfil`, {}, "No se pudo obtener el perfil");
  return res.json();
}

export async function cambiarPassword(id: number, passwordActual: string, passwordNueva: string) {
  // El backend valida el largo mínimo y rechaza contraseñas conocidas: ese
  // mensaje (en `detalle`/`error`) sirve más que un "no se pudo" genérico.
  const res = await fetchApi(
    `${BASE_URL}/usuario/${id}/password`,
    {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ passwordActual, passwordNueva })
    },
    "No se pudo cambiar la contraseña"
  );
  return res.json();
}

export async function login(usuario: string, password: string) {
  // El backend distingue "credenciales inválidas" (401) de "demasiados
  // intentos, esperá" (429): los dos vienen con su propio `error` y
  // `fetchApi` ya prioriza ese texto sobre el mensaje por defecto de acá.
  const res = await fetchApi(
    `${BASE_URL}/usuario/login`,
    {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: usuario, password })
    },
    "Usuario o contraseña incorrectos"
  );
  return res.json();
}

/**
 * Sesión del lado del cliente.
 *
 * El token ya NO vive acá. Viaja en una cookie HttpOnly que pone el servidor al
 * loguear (ver CookieDeSesion.java): el navegador la manda sola en cada pedido
 * y ningún script de la página puede leerla.
 *
 * Lo que queda en localStorage es solo para decidir qué dibujar — el rol y el
 * nombre. No son credenciales: quien las edite a mano se va a ver botones que
 * el servidor le va a rechazar igual, porque los permisos se controlan del otro
 * lado (SecurityConfig).
 */

const BASE_URL = import.meta.env.VITE_API_URL;

export function getRol(): string | null {
  return localStorage.getItem("rol");
}

export function isAdmin(): boolean {
  return getRol() === "ADMIN";
}

/**
 * Permisos sueltos de un EMPLEADO (ver Usuario.java en el backend). ADMIN
 * siempre puede, sin mirar estos flags — igual que ya vale del lado del
 * servidor (SecurityConfig).
 */
export function puedeGestionarProductos(): boolean {
  return isAdmin() || localStorage.getItem("puedeGestionarProductos") === "true";
}

export function puedeVerCaja(): boolean {
  return isAdmin() || localStorage.getItem("puedeVerCaja") === "true";
}

/**
 * Queda por compatibilidad con los doce servicios que la llaman. Ya no aporta
 * nada: la sesión va en la cookie. Se deja vacía en vez de borrarla de golpe
 * para que el cambio no toque doce archivos a la vez.
 */
export function getAuthHeaders(): Record<string, string> {
  return {};
}

let fetchPreparado = false;

/**
 * Deja el `fetch` global listo para trabajar con la sesión en cookie.
 *
 * Hace dos cosas, y las dos tienen que valer para TODOS los pedidos, por eso se
 * envuelve `window.fetch` en vez de tocar cada servicio:
 *
 *  1. `credentials: "include"` — sin esto la cookie no viaja en desarrollo,
 *     donde el front corre en el puerto de Vite y el back en otro. En la app
 *     instalada comparten origen y viajaría igual, pero conviene que las dos
 *     situaciones se comporten igual o los bugs aparecen solo en una.
 *  2. El interceptor de 401 de siempre: si la sesión venció, al login.
 */
/**
 * El 401 del propio login NO es una sesión vencida: es una contraseña
 * equivocada, y la pantalla de login tiene que poder mostrarlo.
 *
 * Sin esta excepción el interceptor se come la respuesta y recarga la página en
 * silencio, con lo cual escribir mal la contraseña no da ningún mensaje y
 * parece que el sistema "no toma" el usuario. Pasó de verdad: antes el
 * interceptor sólo se instalaba desde checkAuth(), que el login no llama; al
 * moverlo al import del módulo pasó a estar activo también acá.
 */
function esElLogin(entrada: RequestInfo | URL): boolean {
  const url =
    typeof entrada === "string"
      ? entrada
      : entrada instanceof URL
        ? entrada.href
        : entrada.url;
  return url.includes("/usuario/login");
}

function prepararFetch(): void {
  if (fetchPreparado) return;
  fetchPreparado = true;

  const fetchOriginal = window.fetch.bind(window);
  window.fetch = async (entrada: RequestInfo | URL, opciones: RequestInit = {}) => {
    const response = await fetchOriginal(entrada, { ...opciones, credentials: "include" });
    if (response.status === 401 && !esElLogin(entrada)) {
      limpiarDatosLocales();
      window.location.href = "../../pages/login/login.html";
      // No resolvemos: dejamos que la navegación ocurra antes de que el
      // caller intente leer la respuesta y muestre su alert() genérico.
      return new Promise<Response>(() => {});
    }
    return response;
  };
}

// Se prepara al importar el módulo y no dentro de checkAuth: el login también
// hace fetch, y es justamente el pedido que necesita recibir la cookie.
prepararFetch();

export function checkAuth(): void {
  prepararFetch();
  // No se puede preguntar por el token: es HttpOnly y no se ve desde acá. El
  // rol alcanza para decidir si mostrar la pantalla; la sesión de verdad la
  // valida el servidor en cada pedido, y si no vale, el interceptor de 401
  // manda al login.
  if (!getRol()) {
    window.location.href = "../../pages/login/login.html";
  }
}

export function checkAdmin(): void {
  checkAuth();
  if (!isAdmin()) {
    window.location.href = "../../pages/ventas/ventas.html";
  }
}

export function getUsername(): string | null {
  return localStorage.getItem("username");
}

function limpiarDatosLocales(): void {
  localStorage.removeItem("token"); // por si quedó de una versión anterior
  localStorage.removeItem("rol");
  localStorage.removeItem("auth");
  localStorage.removeItem("usuario");
  localStorage.removeItem("username");
  localStorage.removeItem("debeCambiarPassword");
  localStorage.removeItem("puedeGestionarProductos");
  localStorage.removeItem("puedeVerCaja");
}

/**
 * Cierra la sesión.
 *
 * Le avisa al servidor, que es quien tiene que apagar la cookie: borrar cosas
 * del navegador ya no alcanza, porque la sesión no vive acá. Si el pedido falla
 * (la app se está cerrando, no hay red) se limpia igual lo local y se sigue: es
 * preferible a dejar al usuario trabado en una pantalla que no puede cerrar.
 */
export async function clearAuth(): Promise<void> {
  try {
    await fetch(`${BASE_URL}/usuario/logout`, { method: "POST" });
  } catch {
    // Sin red no se puede apagar la cookie ahora; vence sola en 24 horas.
  }
  limpiarDatosLocales();
}

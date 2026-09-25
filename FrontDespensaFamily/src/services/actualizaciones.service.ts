import { fetchApi, ErrorApi } from "./api";

const BASE_URL = import.meta.env.VITE_API_URL;

export interface EstadoDeActualizacion {
  versionInstalada: string;
  versionDisponible: string | null;
  hayActualizacion: boolean;
  listaParaAplicar: boolean;
  notas: string | null;
  gestionadoPorLauncher: boolean;
}

/**
 * Qué versión hay instalada y si el launcher encontró una más nueva.
 *
 * El servidor no sale a internet: lee lo que el launcher dejó anotado. Correr
 * el jar suelto es un caso válido y devuelve `gestionadoPorLauncher: false`,
 * que es la forma de decir "acá no hay nadie que pueda actualizar nada".
 */
export async function getEstadoDeActualizacion(): Promise<EstadoDeActualizacion> {
  const res = await fetchApi(`${BASE_URL}/actualizaciones`, {}, "No se pudo consultar el estado de actualizaciones");
  return res.json();
}

/**
 * Aprueba la actualización. El servidor deja una señal y se apaga; el launcher
 * reemplaza el jar y lo vuelve a levantar.
 *
 * Devuelve apenas el servidor acepta, no cuando terminó: lo que sigue es un
 * reinicio, así que la pantalla tiene que esperar a que el servidor vuelva.
 */
export async function aplicarActualizacion(): Promise<void> {
  try {
    await fetchApi(`${BASE_URL}/actualizaciones/aplicar`, { method: "POST" }, "No se pudo aplicar la actualización");
  } catch (err) {
    // El backend responde 409 sin cuerpo en este caso puntual (la descarga
    // todavía no terminó, o ya se aplicó desde otra pantalla): no es un
    // error del sistema, así que no cae en el mensaje genérico de arriba.
    if (err instanceof ErrorApi && err.status === 409) {
      throw new ErrorApi("La actualización todavía no está lista para aplicarse", 409);
    }
    throw err;
  }
}

/**
 * Espera a que el servidor vuelva después del reinicio.
 *
 * Pregunta por la página de login y no por la API: es lo primero que vuelve a
 * responder y no necesita sesión, así que sirve aunque el token haya quedado
 * viejo. Mismo criterio que usa el launcher para esperar el arranque.
 */
export async function esperarAlServidor(limiteMs = 120000): Promise<boolean> {
  const hasta = Date.now() + limiteMs;
  while (Date.now() < hasta) {
    await new Promise(r => setTimeout(r, 2000));
    try {
      const respuesta = await fetch(`${BASE_URL}/src/pages/login/login.html`, {
        method: "HEAD",
        cache: "no-store"
      });
      if (respuesta.ok) return true;
    } catch {
      // Todavía está bajando o levantando: es lo esperado, se sigue esperando.
    }
  }
  return false;
}

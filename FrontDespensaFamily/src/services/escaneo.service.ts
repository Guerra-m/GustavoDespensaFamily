import { getAuthHeaders } from "./auth.service";

const BASE_URL = `${import.meta.env.VITE_API_URL}/escaneo`;

export interface SesionDeEscaneo {
  sesionId: string;
  // Armada por el backend con la IP de la red local, no con
  // window.location.origin: en la app de escritorio esta pantalla suele
  // cargar por localhost, que el celular no puede alcanzar (ver
  // EscaneoController.ipDeLaRedLocal).
  url: string;
}

export async function crearSesionDeEscaneo(): Promise<SesionDeEscaneo> {
  const res = await fetch(`${BASE_URL}/sesiones`, {
    method: "POST",
    headers: getAuthHeaders()
  });
  if (!res.ok) throw new Error("No se pudo abrir el escaneo desde el celular");
  return res.json();
}

export function suscribirseAEscaneo(sesionId: string, onCodigo: (codigo: string) => void): EventSource {
  const url = `${BASE_URL}/sesiones/${sesionId}/eventos`;
  const eventSource = new EventSource(url, { withCredentials: true });
  eventSource.addEventListener("codigo", (e: MessageEvent) => onCodigo(e.data));
  return eventSource;
}

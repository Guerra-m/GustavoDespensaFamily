import { Html5Qrcode } from "html5-qrcode";

// Pagina publica a proposito (ver SecurityConfig): el celular no se loguea,
// solo conoce el UUID de la sesion que viaja en la URL del QR. No importa
// utils/auth.ts ni ningun servicio con getAuthHeaders/interceptor de 401 —
// esta pagina no tiene sesion que pueda vencer.

const BASE_URL = import.meta.env.VITE_API_URL;

const estado = document.getElementById("estado") as HTMLParagraphElement;
const ultimoCodigoBox = document.getElementById("ultimo-codigo") as HTMLDivElement;
const ultimoCodigoValor = document.getElementById("ultimo-codigo-valor") as HTMLElement;

const params = new URLSearchParams(window.location.search);
const sesionId = params.get("sesion");

if (!sesionId) {
  estado.textContent = "Falta el código de sesión. Volvé a escanear el QR desde la caja.";
} else {
  iniciar(sesionId);
}

async function iniciar(sesionId: string) {
  const html5QrCode = new Html5Qrcode("reader");

  try {
    await html5QrCode.start(
      { facingMode: "environment" },
      { fps: 10, qrbox: { width: 260, height: 160 } },
      codigo => enviarCodigo(sesionId, codigo),
      () => {}
    );
    estado.textContent = "Listo — apuntá al código de barras";
  } catch (error) {
    console.error(error);
    estado.textContent = "No se pudo abrir la cámara. Revisá los permisos del navegador.";
  }
}

let enviandoAhora = false;
let ultimoEnviado: string | null = null;
let ultimoEnviadoEn = 0;

async function enviarCodigo(sesionId: string, codigo: string) {
  const ahora = Date.now();
  // Mismo codigo leido en frames seguidos (la camara sigue enfocando el
  // mismo producto) no reenvia hasta pasado un rato, o cada acercada de mano
  // mandaria el mismo producto diez veces.
  if (enviandoAhora || (codigo === ultimoEnviado && ahora - ultimoEnviadoEn < 2500)) return;

  enviandoAhora = true;
  try {
    const res = await fetch(`${BASE_URL}/escaneo/sesiones/${sesionId}/codigos`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ codigo })
    });

    if (res.ok) {
      ultimoEnviado = codigo;
      ultimoEnviadoEn = ahora;
      ultimoCodigoValor.textContent = codigo;
      ultimoCodigoBox.classList.remove("hidden");
      estado.textContent = "Enviado — seguí escaneando";
      if (navigator.vibrate) navigator.vibrate(80);
    } else {
      const texto = await res.text().catch(() => "");
      estado.textContent = texto || "La caja cerró esta sesión. Volvé a escanear el QR.";
    }
  } catch {
    estado.textContent = "Sin conexión con la caja. Revisá el wifi.";
  } finally {
    enviandoAhora = false;
  }
}

import { Html5Qrcode, Html5QrcodeSupportedFormats } from "html5-qrcode";
// @ts-ignore
import * as ZXing from "html5-qrcode/third_party/zxing-js.umd";

// Pagina publica a proposito (ver SecurityConfig): el celular no se loguea,
// solo conoce el UUID de la sesion que viaja en la URL del QR. No importa
// utils/auth.ts ni ningun servicio con getAuthHeaders/interceptor de 401 —
// esta pagina no tiene sesion que pueda vencer.

const BASE_URL = import.meta.env.VITE_API_URL;

const estado = document.getElementById("estado") as HTMLParagraphElement;
const ultimoCodigoBox = document.getElementById("ultimo-codigo") as HTMLDivElement;
const ultimoCodigoValor = document.getElementById("ultimo-codigo-valor") as HTMLElement;

const readerDiv = document.getElementById("reader") as HTMLDivElement;
const contenedorFoto = document.getElementById("contenedor-foto") as HTMLDivElement;
const btnFoto = document.getElementById("btn-foto") as HTMLButtonElement;
const inputFoto = document.getElementById("input-foto") as HTMLInputElement;

function contextoInseguro(): boolean {
  return window.isSecureContext === false || !navigator.mediaDevices;
}

const params = new URLSearchParams(window.location.search);
const sesionId = params.get("sesion");

// Instancia estándar para video en vivo (contexto seguro)
const html5QrCode = new Html5Qrcode("reader", {
  formatsToSupport: [
    Html5QrcodeSupportedFormats.EAN_13,
    Html5QrcodeSupportedFormats.EAN_8,
    Html5QrcodeSupportedFormats.CODE_128,
    Html5QrcodeSupportedFormats.CODE_39,
    Html5QrcodeSupportedFormats.UPC_A,
    Html5QrcodeSupportedFormats.UPC_E,
    Html5QrcodeSupportedFormats.QR_CODE,
  ],
  verbose: false,
});

if (!sesionId) {
  estado.textContent = "Falta el código de sesión. Volvé a escanear el QR desde la caja.";
} else if (contextoInseguro()) {
  // En HTTP por LAN el navegador bloquea getUserMedia (video en vivo).
  // Activamos el modo foto nativo con el decodificador avanzado.
  readerDiv.classList.add("hidden");
  contenedorFoto.classList.remove("hidden");
  estado.textContent = "Tocá el botón para abrir la cámara y escanear";
  configurarModoFoto(sesionId);
} else {
  iniciar(sesionId);
}

// -------------------------------------------------------------
// DECODIFICADOR ROBUSTO MULTI-ESTRATEGIA PARA FOTOS
// -------------------------------------------------------------

function crearLectorZXing(): any {
  const hints = new Map();
  hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS, [
    ZXing.BarcodeFormat.EAN_13,
    ZXing.BarcodeFormat.EAN_8,
    ZXing.BarcodeFormat.CODE_128,
    ZXing.BarcodeFormat.CODE_39,
    ZXing.BarcodeFormat.UPC_A,
    ZXing.BarcodeFormat.UPC_E,
    ZXing.BarcodeFormat.ITF,
    ZXing.BarcodeFormat.QR_CODE,
  ]);
  // TRY_HARDER: true es CRUCIAL para fotos estáticas (escanea todas las filas de píxeles)
  hints.set(ZXing.DecodeHintType.TRY_HARDER, true);
  return new (ZXing as any).MultiFormatReader(false, hints);
}

function probarBinarizadores(canvas: HTMLCanvasElement): string | null {
  const reader = crearLectorZXing();
  const luminanceSource = new (ZXing as any).HTMLCanvasElementLuminanceSource(canvas);

  // 1. HybridBinarizer (contraste adaptativo local)
  try {
    const bitmap = new (ZXing as any).BinaryBitmap(new (ZXing as any).HybridBinarizer(luminanceSource));
    const result = reader.decode(bitmap);
    if (result && result.getText()) return result.getText();
  } catch {}

  // 2. GlobalHistogramBinarizer (ideal para fotos con flash, sombras o reflejo en plástico)
  if ((ZXing as any).GlobalHistogramBinarizer) {
    try {
      const bitmap = new (ZXing as any).BinaryBitmap(new (ZXing as any).GlobalHistogramBinarizer(luminanceSource));
      const result = reader.decode(bitmap);
      if (result && result.getText()) return result.getText();
    } catch {}
  }

  // 3. InvertedLuminanceSource (para envases oscuros o códigos claros sobre fondo oscuro)
  if ((ZXing as any).InvertedLuminanceSource) {
    try {
      const inverted = new (ZXing as any).InvertedLuminanceSource(luminanceSource);
      const bitmap = new (ZXing as any).BinaryBitmap(new (ZXing as any).HybridBinarizer(inverted));
      const result = reader.decode(bitmap);
      if (result && result.getText()) return result.getText();
    } catch {}
  }

  return null;
}

/**
 * Escala una imagen manteniendo aspect ratio a un ancho máximo.
 */
function cargarImagenEnCanvas(file: File, maxDim: number): Promise<HTMLCanvasElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      let w = img.width;
      let h = img.height;
      if (w > maxDim || h > maxDim) {
        if (w > h) {
          h = Math.round((h * maxDim) / w);
          w = maxDim;
        } else {
          w = Math.round((w * maxDim) / h);
          h = maxDim;
        }
      }
      const canvas = document.createElement("canvas");
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext("2d");
      if (!ctx) {
        URL.revokeObjectURL(img.src);
        return reject(new Error("No 2d context"));
      }
      ctx.drawImage(img, 0, 0, w, h);
      URL.revokeObjectURL(img.src);
      resolve(canvas);
    };
    img.onerror = (err) => {
      URL.revokeObjectURL(img.src);
      reject(err);
    };
    img.src = URL.createObjectURL(file);
  });
}

function rotarCanvas(fuente: HTMLCanvasElement, grados: number): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  if (grados === 90 || grados === 270) {
    canvas.width = fuente.height;
    canvas.height = fuente.width;
  } else {
    canvas.width = fuente.width;
    canvas.height = fuente.height;
  }
  const ctx = canvas.getContext("2d");
  if (ctx) {
    ctx.translate(canvas.width / 2, canvas.height / 2);
    ctx.rotate((grados * Math.PI) / 180);
    ctx.drawImage(fuente, -fuente.width / 2, -fuente.height / 2);
  }
  return canvas;
}

function recortarCentro(fuente: HTMLCanvasElement, porcentaje: number): HTMLCanvasElement {
  const canvas = document.createElement("canvas");
  const cw = Math.round(fuente.width * porcentaje);
  const ch = Math.round(fuente.height * porcentaje);
  const sx = Math.round((fuente.width - cw) / 2);
  const sy = Math.round((fuente.height - ch) / 2);

  canvas.width = cw;
  canvas.height = ch;
  const ctx = canvas.getContext("2d");
  if (ctx) {
    ctx.drawImage(fuente, sx, sy, cw, ch, 0, 0, cw, ch);
  }
  return canvas;
}

/**
 * Pipeline multi-fase para garantizar lectura en fotos reales de celular:
 * 1. BarcodeDetector nativo (si el entorno lo expone).
 * 2. ZXing con TRY_HARDER en resolución óptima (1200px) en horizontal.
 * 3. ZXing en vertical (rotada 90° para celulares en retrato).
 * 4. ZXing recortando el centro del producto al 65% (elimina bordes molestos).
 * 5. ZXing recortando el centro rotado 90°.
 */
async function decodificarCodigo(file: File): Promise<string> {
  // Fase 1: BarcodeDetector nativo (si está soportado)
  if ("BarcodeDetector" in window) {
    try {
      const detector = new (window as any).BarcodeDetector({
        formats: ["ean_13", "ean_8", "code_128", "code_39", "upc_a", "upc_e", "qr_code"]
      });
      const bitmap = await createImageBitmap(file);
      const barcodes = await detector.detect(bitmap);
      if (barcodes && barcodes.length > 0 && barcodes[0].rawValue) {
        return barcodes[0].rawValue;
      }
    } catch {}
  }

  // Fase 2: Escala a 1200px (sweet spot para lectura óptica de códigos de barras)
  const canvas = await cargarImagenEnCanvas(file, 1200);

  // Intento 1: Orientación original
  let codigo = probarBinarizadores(canvas);
  if (codigo) return codigo;

  // Intento 2: Rotada 90° (por si la foto se sacó sosteniendo el teléfono en vertical)
  const canvas90 = rotarCanvas(canvas, 90);
  codigo = probarBinarizadores(canvas90);
  if (codigo) return codigo;

  // Intento 3: Recorte central al 65% (donde la persona enfoca el código y elimina la mesa/fondo)
  const canvasCentro = recortarCentro(canvas, 0.65);
  codigo = probarBinarizadores(canvasCentro);
  if (codigo) return codigo;

  // Intento 4: Recorte central rotado 90°
  const canvasCentro90 = rotarCanvas(canvasCentro, 90);
  codigo = probarBinarizadores(canvasCentro90);
  if (codigo) return codigo;

  // Intento 5: Fallback final con html5-qrcode
  return await html5QrCode.scanFile(file, false);
}

function configurarModoFoto(sesionId: string) {
  btnFoto?.addEventListener("click", () => {
    inputFoto.click();
  });

  inputFoto?.addEventListener("change", async () => {
    const file = inputFoto.files?.[0];
    if (!file) return;

    estado.textContent = "Analizando código de barras...";
    btnFoto.disabled = true;

    try {
      const codigo = await decodificarCodigo(file);
      await enviarCodigo(sesionId, codigo);
    } catch (error) {
      console.error(error);
      estado.textContent = "No se detectó el código. Apuntá de más cerca y con buena luz.";
    } finally {
      btnFoto.disabled = false;
      inputFoto.value = "";
    }
  });
}

async function iniciar(sesionId: string) {
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
    readerDiv.classList.add("hidden");
    contenedorFoto.classList.remove("hidden");
    estado.textContent = "No se pudo abrir el video en vivo. Podés escanear con foto:";
    configurarModoFoto(sesionId);
  }
}

let enviandoAhora = false;
let ultimoEnviado: string | null = null;
let ultimoEnviadoEn = 0;

async function enviarCodigo(sesionId: string, codigo: string) {
  const ahora = Date.now();
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

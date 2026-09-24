import { checkAuth, isAdmin } from "../../services/auth.service";
checkAuth();

import {
  getConfiguracion,
  actualizarConfiguracion,
  aplicarGananciaGlobal,
  getEstadoIa,
  guardarClaveIa,
  quitarClaveIa
} from "../../services/configuracion.service";
import type { EstadoIa } from "../../models/EstadoIa";
import { confirmar } from "../../services/confirm.service";

// Ajustes es admin-only del lado del backend (SecurityConfig) — acá además
// se redirige de una si un empleado llega por URL directa, en vez de
// mostrarle una pantalla que de todas formas le va a devolver 403 en cada
// acción.
if (!isAdmin()) {
  window.location.href = "../../pages/home/home.html";
}

const toast = document.getElementById("toast") as HTMLDivElement;

const inputGananciaGlobal = document.getElementById("input-ganancia-global") as HTMLInputElement;
const btnAplicarGanancia = document.getElementById("btn-aplicar-ganancia") as HTMLButtonElement;
const resultadoGanancia = document.getElementById("resultado-ganancia") as HTMLParagraphElement;

const inputUmbralStock = document.getElementById("input-umbral-stock") as HTMLInputElement;
const btnGuardarUmbral = document.getElementById("btn-guardar-umbral") as HTMLButtonElement;

const inputNombreNegocio = document.getElementById("input-nombre-negocio") as HTMLInputElement;
const btnGuardarNombre = document.getElementById("btn-guardar-nombre") as HTMLButtonElement;

const badgeEstadoIa = document.getElementById("badge-estado-ia") as HTMLSpanElement;
const inputClaveIa = document.getElementById("input-clave-ia") as HTMLInputElement;
const btnGuardarClaveIa = document.getElementById("btn-guardar-clave-ia") as HTMLButtonElement;
const btnQuitarClaveIa = document.getElementById("btn-quitar-clave-ia") as HTMLButtonElement;
const errorClaveIa = document.getElementById("error-clave-ia") as HTMLParagraphElement;

function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  toast.classList.toggle("error", error);
  setTimeout(() => toast.classList.add("hidden"), 3500);
}

async function cargarConfiguracion() {
  try {
    const config = await getConfiguracion();
    inputGananciaGlobal.value = String(config.gananciaPorDefecto);
    inputUmbralStock.value = String(config.umbralStockBajo);
    inputNombreNegocio.value = config.nombreNegocio;
  } catch (error) {
    console.error(error);
    mostrarToast("Error al cargar la configuración", true);
  }
}

btnAplicarGanancia.addEventListener("click", async () => {
  const porcentaje = Number(inputGananciaGlobal.value);
  if (!Number.isFinite(porcentaje) || porcentaje < 0) {
    mostrarToast("Ingresá un porcentaje válido", true);
    return;
  }

  const confirmado = await confirmar(
    `Esto va a recalcular el precio de venta de TODOS los productos activos con ${porcentaje}% de ganancia. ¿Confirmás?`
  );
  if (!confirmado) return;

  const textoOriginal = btnAplicarGanancia.textContent;
  btnAplicarGanancia.disabled = true;
  btnAplicarGanancia.textContent = "Aplicando...";
  resultadoGanancia.classList.add("hidden");

  try {
    const { productosActualizados } = await aplicarGananciaGlobal(porcentaje);
    resultadoGanancia.textContent = `✓ Se actualizaron ${productosActualizados} producto${productosActualizados === 1 ? "" : "s"}.`;
    resultadoGanancia.classList.remove("hidden", "error");
    mostrarToast("Ganancia global aplicada correctamente");
  } catch (error) {
    console.error(error);
    mostrarToast(error instanceof Error ? error.message : "Error al aplicar la ganancia global", true);
  } finally {
    btnAplicarGanancia.disabled = false;
    btnAplicarGanancia.textContent = textoOriginal;
  }
});

btnGuardarUmbral.addEventListener("click", async () => {
  const umbral = Number(inputUmbralStock.value);
  if (!Number.isFinite(umbral) || umbral < 0) {
    mostrarToast("Ingresá un valor válido", true);
    return;
  }

  try {
    await actualizarConfiguracion({ umbralStockBajo: umbral });
    mostrarToast("Umbral de stock bajo guardado");
  } catch (error) {
    console.error(error);
    mostrarToast(error instanceof Error ? error.message : "Error al guardar", true);
  }
});

btnGuardarNombre.addEventListener("click", async () => {
  const nombre = inputNombreNegocio.value.trim();
  if (!nombre) {
    mostrarToast("El nombre no puede estar vacío", true);
    return;
  }

  try {
    await actualizarConfiguracion({ nombreNegocio: nombre });
    mostrarToast("Nombre guardado. Se ve reflejado al recargar la página.");
  } catch (error) {
    console.error(error);
    mostrarToast(error instanceof Error ? error.message : "Error al guardar", true);
  }
});

// ---------------- ASISTENTE DE IA ----------------

// textContent, nunca innerHTML: el estado viene del backend, pero DF-SEC-001
// ya mostro que "esto no lo escribe el usuario" no es excusa para relajar
// esto (FE-03).
let estadoIaActual: EstadoIa | null = null;

// Con GEMINI_API_KEY definida en Windows el backend responde 409 a guardar y
// quitar: se deshabilita todo en vez de dejar botones que siempre fallan.
function aplicarHabilitadoIa() {
  const porVariable = estadoIaActual?.origen === "variable-de-entorno";
  inputClaveIa.disabled = porVariable;
  btnGuardarClaveIa.disabled = porVariable;
  btnQuitarClaveIa.disabled = porVariable || !estadoIaActual?.configurada;
}

function pintarEstadoIa(estado: EstadoIa) {
  estadoIaActual = estado;
  badgeEstadoIa.classList.remove("configurada", "no-configurada");
  if (!estado.configurada) {
    badgeEstadoIa.textContent = "No configurado";
    badgeEstadoIa.classList.add("no-configurada");
  } else {
    badgeEstadoIa.textContent = estado.origen === "variable-de-entorno"
      ? "Configurado (variable de entorno de Windows)"
      : "Configurado";
    badgeEstadoIa.classList.add("configurada");
  }
  aplicarHabilitadoIa();
}

function mostrarErrorClaveIa(mensaje: string) {
  errorClaveIa.textContent = mensaje;
  errorClaveIa.classList.remove("hidden");
}

function limpiarErrorClaveIa() {
  errorClaveIa.textContent = "";
  errorClaveIa.classList.add("hidden");
}

async function cargarEstadoIa() {
  try {
    pintarEstadoIa(await getEstadoIa());
  } catch (error) {
    console.error(error);
    badgeEstadoIa.textContent = "No se pudo consultar";
  }
}

btnGuardarClaveIa.addEventListener("click", async () => {
  const apiKey = inputClaveIa.value.trim();
  limpiarErrorClaveIa();

  if (!apiKey) {
    mostrarErrorClaveIa("Pegá una clave antes de guardar");
    return;
  }

  const textoOriginal = btnGuardarClaveIa.textContent;
  btnGuardarClaveIa.disabled = true;
  btnGuardarClaveIa.textContent = "Guardando...";

  try {
    pintarEstadoIa(await guardarClaveIa(apiKey));
    inputClaveIa.value = "";
    mostrarToast("Clave del asistente de IA guardada");
  } catch (error) {
    console.error(error);
    mostrarErrorClaveIa(error instanceof Error ? error.message : "Error al guardar la clave");
  } finally {
    btnGuardarClaveIa.textContent = textoOriginal;
    aplicarHabilitadoIa();
  }
});

btnQuitarClaveIa.addEventListener("click", async () => {
  const confirmado = await confirmar("¿Quitar la clave del asistente de IA? El asistente va a dejar de funcionar hasta que cargues una nueva.");
  if (!confirmado) return;

  limpiarErrorClaveIa();
  const textoOriginal = btnQuitarClaveIa.textContent;
  btnQuitarClaveIa.disabled = true;
  btnQuitarClaveIa.textContent = "Quitando...";

  try {
    pintarEstadoIa(await quitarClaveIa());
    mostrarToast("Clave del asistente de IA quitada");
  } catch (error) {
    console.error(error);
    mostrarErrorClaveIa(error instanceof Error ? error.message : "Error al quitar la clave");
  } finally {
    btnQuitarClaveIa.textContent = textoOriginal;
    aplicarHabilitadoIa();
  }
});

// ---------------- INIT ----------------
cargarConfiguracion();
cargarEstadoIa();

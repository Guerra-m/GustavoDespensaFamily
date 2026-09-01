import { checkAuth, isAdmin } from "../../services/auth.service";
checkAuth();

import { getConfiguracion, actualizarConfiguracion, aplicarGananciaGlobal } from "../../services/configuracion.service";
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

// ---------------- INIT ----------------
cargarConfiguracion();

import { habilitarCierreConEscape } from "../../utils/modales";
import { checkAuth, isAdmin, puedeVerCaja } from "../../services/auth.service";
checkAuth();

// Sin permiso de caja, esta pantalla no es de esta persona. El backend ya
// rechaza los pedidos (ver SecurityConfig), así que sin esto quedaría una
// pantalla cargada a medias con todo en cero y un error al pie: peor que no
// entrar. La seguridad la pone el servidor; esto es para que no se vea rota.
if (!isAdmin() && !puedeVerCaja()) {
  location.replace("../ventas/ventas.html");
}

import { confirmar } from "../../services/confirm.service";
import { esc, formatearMoneda } from "../../utils/format";
import {
  getEstadoCaja,
  getUltimosMovimientos,
  getMovimientos,
  registrarMovimiento,
  revertirMovimiento,
  registrarArqueo
} from "../../services/caja.service";
import type { MovimientoCaja } from "../../models/MovimientoCaja";
import type { CategoriaMovimiento } from "../../models/CategoriaMovimiento";
import type { TipoMovimiento } from "../../models/TipoMovimiento";
import type { MetodoPago } from "../../models/MetodoPago";
import { mostrarToast } from "../../utils/toast";

// Adentro de la pantalla hay un segundo nivel: quien llegó hasta acá tiene
// permiso para MIRAR la caja, pero cargar un movimiento, revertirlo o arquear
// siguen siendo del dueño — mover plata no es lo mismo que verla. El backend
// ya lo exige (SecurityConfig); esto solo evita mostrar botones que van a
// terminar en 403.
document.querySelectorAll<HTMLElement>(".caja-solo-admin").forEach(el => {
  if (isAdmin()) el.classList.remove("hidden");
});

// El historial completo (con gastos y retiros) lo ve el dueño o quien tenga
// PERM_CAJA — ver Usuario.java.
document.querySelectorAll<HTMLElement>(".caja-solo-caja").forEach(el => {
  if (isAdmin() || puedeVerCaja()) el.classList.remove("hidden");
});

function formatearHora(iso: string): string {
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return "";
  return fecha.toLocaleTimeString("es-AR", { hour: "2-digit", minute: "2-digit" });
}

const ETIQUETAS_CATEGORIA: Record<CategoriaMovimiento, string> = {
  VENTA: "Venta",
  COMPRA: "Compra",
  GASTO: "Gasto",
  RETIRO: "Retiro",
  APORTE: "Aporte",
  AJUSTE: "Ajuste",
  // El pago a un empleado. Sin esta línea la columna Categoría mostraba
  // "undefined" en cada sueldo pagado (ver CategoriaMovimiento.ts).
  SUELDO: "Sueldo"
};

// ---------------- Estado de caja ----------------

const spanSaldoTotal = document.getElementById("saldo-total") as HTMLSpanElement;
const spanEfectivo = document.getElementById("efectivo-en-caja") as HTMLSpanElement;
const spanElectronico = document.getElementById("saldo-electronico") as HTMLSpanElement;
const spanIngresosHoy = document.getElementById("ingresos-hoy") as HTMLParagraphElement;
const spanEgresosHoy = document.getElementById("egresos-hoy") as HTMLParagraphElement;
const spanResultadoHoy = document.getElementById("resultado-hoy") as HTMLParagraphElement;
const spanVentasNetasHoy = document.getElementById("ventas-netas-hoy") as HTMLParagraphElement | null;
const spanAnulacionesHoy = document.getElementById("anulaciones-hoy") as HTMLParagraphElement | null;

async function cargarEstado() {
  try {
    const estado = await getEstadoCaja();
    spanSaldoTotal.textContent = formatearMoneda(estado.saldoTotal);
    spanEfectivo.textContent = formatearMoneda(estado.efectivoEnCaja);
    spanElectronico.textContent = formatearMoneda(estado.saldoElectronico);
    spanIngresosHoy.textContent = formatearMoneda(estado.ingresosDeHoy);
    spanEgresosHoy.textContent = formatearMoneda(estado.egresosDeHoy);
    spanResultadoHoy.textContent = formatearMoneda(estado.resultadoDeHoy);
    // DF-UX-006: anular una venta ya no infla "egresos de hoy" -- se
    // muestran aparte para que se vea que fue una anulacion, no un gasto.
    if (spanVentasNetasHoy) spanVentasNetasHoy.textContent = formatearMoneda(estado.ventasNetasDeHoy);
    if (spanAnulacionesHoy) spanAnulacionesHoy.textContent = formatearMoneda(estado.anulacionesDeHoy);
    spanResultadoHoy.classList.toggle("caja-stat-ingreso", estado.resultadoDeHoy >= 0);
    spanResultadoHoy.classList.toggle("caja-stat-egreso", estado.resultadoDeHoy < 0);
  } catch (error) {
    mostrarToast((error as Error).message || "Error al cargar el estado de caja", true);
  }
}

// ---------------- Movimientos recientes ----------------

const cuerpoMovimientos = document.getElementById("cuerpo-movimientos") as HTMLTableSectionElement;
const movimientosVacio = document.getElementById("movimientos-vacio") as HTMLParagraphElement;

function filaMovimiento(m: MovimientoCaja): HTMLTableRowElement {
  const tr = document.createElement("tr");
  const esIngreso = m.tipo === "INGRESO";
  const signo = esIngreso ? "+" : "−";
  const claseMonto = esIngreso ? "caja-monto-ingreso" : "caja-monto-egreso";

  // Revertir solo tiene sentido sobre el movimiento ORIGINAL: uno que ya se
  // revirtió (m.revertido) o que es en sí mismo una reversión (m.revierteA)
  // no puede volver a revertirse — el backend lo rechaza igual, esto solo
  // evita ofrecer un botón que va a fallar.
  const puedeRevertirse = isAdmin() && !m.revertido && !m.revierteA;

  tr.innerHTML = `
    <td>${formatearHora(m.fechaHora)}</td>
    <td>${esc(m.descripcion) || ETIQUETAS_CATEGORIA[m.categoria]}
      ${m.revertido ? '<span class="caja-etiqueta-revertido">Revertido</span>' : ""}
      ${m.revierteA ? '<span class="caja-etiqueta-revertido">Reversión</span>' : ""}
    </td>
    <td>${ETIQUETAS_CATEGORIA[m.categoria]}</td>
    <td>${m.metodoPago}</td>
    <td class="${claseMonto}">${signo}${formatearMoneda(m.monto)}</td>
    <td>${esc(m.usuario) || "—"}</td>
    <td class="caja-solo-admin ${isAdmin() ? "" : "hidden"}">
      ${puedeRevertirse ? `<button class="btn-revertir-movimiento" data-id="${m.id}">Revertir</button>` : ""}
    </td>
  `;

  const btnRevertir = tr.querySelector(".btn-revertir-movimiento") as HTMLButtonElement | null;
  btnRevertir?.addEventListener("click", async () => {
    const confirmado = await confirmar(
      `¿Revertir este movimiento de ${formatearMoneda(m.monto)}? Queda un movimiento nuevo en sentido contrario, este no se borra.`
    );
    if (!confirmado) return;
    try {
      await revertirMovimiento(m.id);
      mostrarToast("Movimiento revertido");
      await Promise.all([cargarEstado(), cargarMovimientos()]);
    } catch (error) {
      mostrarToast((error as Error).message || "No se pudo revertir", true);
    }
  });

  return tr;
}

async function cargarMovimientos() {
  try {
    const movimientos = await getUltimosMovimientos();
    cuerpoMovimientos.innerHTML = "";
    movimientos.forEach(m => cuerpoMovimientos.appendChild(filaMovimiento(m)));
    movimientosVacio.classList.toggle("hidden", movimientos.length > 0);
  } catch (error) {
    mostrarToast((error as Error).message || "Error al cargar los movimientos", true);
  }
}

// ---------------- Registrar movimiento (modal) ----------------

const modal = document.getElementById("modal-movimiento") as HTMLDivElement;
const btnNuevoMovimiento = document.getElementById("btn-nuevo-movimiento") as HTMLButtonElement;
const btnCancelarMovimiento = document.getElementById("btn-cancelar-movimiento") as HTMLButtonElement;
const formMovimiento = document.getElementById("form-movimiento") as HTMLFormElement;
const selectTipo = document.getElementById("select-tipo") as HTMLSelectElement;
const selectCategoria = document.getElementById("select-categoria") as HTMLSelectElement;

// Categorías por tipo: cargar un ingreso "GASTO" o un egreso "APORTE" no
// tiene sentido de negocio, así que el desplegable solo ofrece las que
// corresponden. VENTA y COMPRA no aparecen nunca: esas las genera el propio
// flujo de la venta o la compra (ver CajaService.registrarMovimientoManual,
// que las rechaza si llegaran igual por acá).
//
// SUELDO tampoco va acá, y no es un olvido: un sueldo se registra desde
// Empleados (POST /empleados/{id}/pagar), que es lo que deja el pago atado a
// la persona. El backend lo rechaza si llega por la carga manual, igual que a
// VENTA y COMPRA.
const CATEGORIAS_POR_TIPO: Record<TipoMovimiento, CategoriaMovimiento[]> = {
  EGRESO: ["GASTO", "RETIRO", "AJUSTE"],
  INGRESO: ["APORTE", "AJUSTE"]
};

function actualizarOpcionesDeCategoria() {
  const tipo = selectTipo.value as TipoMovimiento;
  selectCategoria.innerHTML = CATEGORIAS_POR_TIPO[tipo]
    .map(c => `<option value="${c}">${ETIQUETAS_CATEGORIA[c]}</option>`)
    .join("");
}

selectTipo.addEventListener("change", actualizarOpcionesDeCategoria);
actualizarOpcionesDeCategoria();

function abrirModal() {
  formMovimiento.reset();
  actualizarOpcionesDeCategoria();
  modal.classList.remove("hidden");
}

function cerrarModal() {
  modal.classList.add("hidden");
}

btnNuevoMovimiento.addEventListener("click", abrirModal);
btnCancelarMovimiento.addEventListener("click", cerrarModal);
modal.addEventListener("click", e => {
  if (e.target === modal) cerrarModal();
});

formMovimiento.addEventListener("submit", async e => {
  e.preventDefault();
  const datos = new FormData(formMovimiento);

  try {
    await registrarMovimiento({
      tipo: datos.get("tipo") as TipoMovimiento,
      categoria: datos.get("categoria") as CategoriaMovimiento,
      monto: Number(datos.get("monto")),
      metodoPago: datos.get("metodoPago") as MetodoPago,
      descripcion: String(datos.get("descripcion") || "")
    });
    mostrarToast("Movimiento registrado");
    cerrarModal();
    await Promise.all([cargarEstado(), cargarMovimientos()]);
  } catch (error) {
    mostrarToast((error as Error).message || "No se pudo registrar el movimiento", true);
  }
});

// ---------------- Arqueo (Fase 6) ----------------

const modalArqueo = document.getElementById("modal-arqueo") as HTMLDivElement;
const btnArqueo = document.getElementById("btn-arqueo") as HTMLButtonElement;
const btnCancelarArqueo = document.getElementById("btn-cancelar-arqueo") as HTMLButtonElement;
const formArqueo = document.getElementById("form-arqueo") as HTMLFormElement;
const spanArqueoEsperado = document.getElementById("arqueo-esperado") as HTMLSpanElement;
const divArqueoResultado = document.getElementById("arqueo-resultado") as HTMLDivElement;

async function abrirModalArqueo() {
  formArqueo.reset();
  divArqueoResultado.classList.add("hidden");
  try {
    const estado = await getEstadoCaja();
    spanArqueoEsperado.textContent = formatearMoneda(estado.efectivoEnCaja);
  } catch {
    spanArqueoEsperado.textContent = "—";
  }
  modalArqueo.classList.remove("hidden");
}

function cerrarModalArqueo() {
  modalArqueo.classList.add("hidden");
}

btnArqueo.addEventListener("click", abrirModalArqueo);
btnCancelarArqueo.addEventListener("click", cerrarModalArqueo);
modalArqueo.addEventListener("click", e => {
  if (e.target === modalArqueo) cerrarModalArqueo();
});

formArqueo.addEventListener("submit", async e => {
  e.preventDefault();
  const datos = new FormData(formArqueo);

  try {
    const resultado = await registrarArqueo({
      efectivoContado: Number(datos.get("efectivoContado")),
      descripcion: String(datos.get("descripcion") || "") || undefined
    });

    if (resultado.diferencia === 0) {
      // DF-UX-006: aunque no hay ajuste de plata, SI queda una constancia en
      // el historial (movimiento de $0) de que alguien conto el cajon hoy.
      divArqueoResultado.className = "caja-arqueo-resultado caja-arqueo-exacto";
      divArqueoResultado.textContent = "El cajón cuadra exacto. Quedó registrado en el historial, sin ajustar nada.";
    } else {
      const signo = resultado.diferencia > 0 ? "sobraron" : "faltaron";
      divArqueoResultado.className = "caja-arqueo-resultado caja-arqueo-diferencia";
      divArqueoResultado.textContent =
        `Se registró un ajuste: ${signo} ${formatearMoneda(Math.abs(resultado.diferencia))}.`;
    }
    divArqueoResultado.classList.remove("hidden");

    await Promise.all([cargarEstado(), cargarMovimientos()]);
  } catch (error) {
    mostrarToast((error as Error).message || "No se pudo registrar el arqueo", true);
  }
});

// ---------------- Historial con filtros (Fase 7) ----------------
// Solo se arma si el elemento existe: la sección entera es .caja-solo-caja,
// así que un empleado sin PERM_CAJA nunca corre este bloque contra un
// endpoint que le daría 403 igual.
const seccionHistorial = document.querySelector(".caja-historial") as HTMLElement | null;

if (seccionHistorial && (isAdmin() || puedeVerCaja())) {
  const inputFiltroDesde = document.getElementById("filtro-desde") as HTMLInputElement;
  const inputFiltroHasta = document.getElementById("filtro-hasta") as HTMLInputElement;
  const selectFiltroTipo = document.getElementById("filtro-tipo") as HTMLSelectElement;
  const selectFiltroCategoria = document.getElementById("filtro-categoria") as HTMLSelectElement;
  const selectFiltroMetodo = document.getElementById("filtro-metodo") as HTMLSelectElement;
  const btnBuscarHistorial = document.getElementById("btn-buscar-historial") as HTMLButtonElement;
  const cuerpoHistorial = document.getElementById("cuerpo-historial") as HTMLTableSectionElement;
  const historialVacio = document.getElementById("historial-vacio") as HTMLParagraphElement;

  const hoy = new Date().toISOString().split("T")[0];
  const haceUnMes = new Date();
  haceUnMes.setMonth(haceUnMes.getMonth() - 1);
  inputFiltroDesde.value = haceUnMes.toISOString().split("T")[0];
  inputFiltroHasta.value = hoy;

  function filaHistorial(m: MovimientoCaja): HTMLTableRowElement {
    const tr = document.createElement("tr");
    const esIngreso = m.tipo === "INGRESO";
    const signo = esIngreso ? "+" : "−";
    const claseMonto = esIngreso ? "caja-monto-ingreso" : "caja-monto-egreso";
    tr.innerHTML = `
      <td>${formatearFechaCompleta(m.fechaHora)}</td>
      <td>${esc(m.descripcion) || ETIQUETAS_CATEGORIA[m.categoria]}</td>
      <td>${esIngreso ? "Ingreso" : "Egreso"}</td>
      <td>${ETIQUETAS_CATEGORIA[m.categoria]}</td>
      <td>${m.metodoPago}</td>
      <td class="${claseMonto}">${signo}${formatearMoneda(m.monto)}</td>
      <td>${esc(m.usuario) || "—"}</td>
    `;
    return tr;
  }

  function formatearFechaCompleta(iso: string): string {
    const fecha = new Date(iso);
    if (Number.isNaN(fecha.getTime())) return "";
    return fecha.toLocaleString("es-AR", {
      day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"
    });
  }

  async function buscarHistorial() {
    if (!inputFiltroDesde.value || !inputFiltroHasta.value) {
      mostrarToast("Seleccioná desde y hasta", true);
      return;
    }
    try {
      // El backend filtra por rango de fecha; tipo/categoría/método se
      // filtran acá porque son pocos movimientos por período en un negocio
      // chico — no justifica cuatro parámetros más en el endpoint todavía.
      let movimientos = await getMovimientos(inputFiltroDesde.value, inputFiltroHasta.value);

      if (selectFiltroTipo.value) {
        movimientos = movimientos.filter(m => m.tipo === selectFiltroTipo.value);
      }
      if (selectFiltroCategoria.value) {
        movimientos = movimientos.filter(m => m.categoria === selectFiltroCategoria.value);
      }
      if (selectFiltroMetodo.value) {
        movimientos = movimientos.filter(m => m.metodoPago === selectFiltroMetodo.value);
      }

      cuerpoHistorial.innerHTML = "";
      movimientos.forEach(m => cuerpoHistorial.appendChild(filaHistorial(m)));
      historialVacio.classList.toggle("hidden", movimientos.length > 0);
    } catch (error) {
      mostrarToast((error as Error).message || "Error al buscar el historial", true);
    }
  }

  btnBuscarHistorial.addEventListener("click", buscarHistorial);
  buscarHistorial();
}

// ---------------- INIT ----------------
cargarEstado();
cargarMovimientos();

// Escape cierra el modal visible en esta pantalla (misma logica en todas las pantallas con modal).
habilitarCierreConEscape();

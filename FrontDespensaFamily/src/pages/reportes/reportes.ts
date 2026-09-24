import { checkAdmin } from "../../services/auth.service";
checkAdmin();

import type { ProductoBajoStock, Movimiento, ProductoMasVendido, ResumenVentas, VentasPorCategoria, GananciaPorFecha, Rentabilidad } from "../../models/Reportes";
import {
  getProductosBajoStock,
  getProductosMasVendidos,
  getMovimientos,
  getResumenVentas,
  getVentasPorCategoria,
  getGananciaPorFecha,
  getRentabilidad
} from "../../services/reportes.service";
import Chart from "chart.js/auto";
import { formatearCantidad, esc } from "../../utils/format";

// ---------------- DOM ----------------
const toast = document.getElementById("toast") as HTMLDivElement;

function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  toast.classList.toggle("error", error);
  setTimeout(() => toast.classList.add("hidden"), 2500);
}

const tablaBajoStock = document.getElementById("tabla-bajo-stock") as HTMLTableElement;
const tablaMasVendidos = document.getElementById("tabla-mas-vendidos") as HTMLTableElement;
const tablaMovimientos = document.getElementById("tabla-movimientos") as HTMLTableElement;
const tablaVentasCategoria = document.getElementById("tabla-ventas-categoria") as HTMLTableElement;
const resumenContainer = document.getElementById("resumen-container") as HTMLDivElement;

const tablaGanancia = document.getElementById("tabla-ganancia") as HTMLTableElement;
const canvasMasVendidos = document.getElementById("chart-mas-vendidos") as HTMLCanvasElement;
const canvasMovimientos = document.getElementById("chart-movimientos") as HTMLCanvasElement;
const canvasVentasCategoria = document.getElementById("chart-ventas-categoria") as HTMLCanvasElement;
const canvasGanancia = document.getElementById("chart-ganancia") as HTMLCanvasElement;

let chartMasVendidos: Chart | null = null;
let chartMovimientos: Chart | null = null;
let chartVentasCategoria: Chart | null = null;
let chartGanancia: Chart | null = null;

const inputMasVendidosDesde = document.getElementById("fecha-desde") as HTMLInputElement;
const inputMasVendidosHasta = document.getElementById("fecha-hasta") as HTMLInputElement;
const inputMovDesde = document.getElementById("mov-desde") as HTMLInputElement;
const inputMovHasta = document.getElementById("mov-hasta") as HTMLInputElement;
const inputCatDesde = document.getElementById("cat-desde") as HTMLInputElement;
const inputCatHasta = document.getElementById("cat-hasta") as HTMLInputElement;
const inputGanDesde = document.getElementById("gan-desde") as HTMLInputElement;
const inputGanHasta = document.getElementById("gan-hasta") as HTMLInputElement;
const inputRentDesde = document.getElementById("rent-desde") as HTMLInputElement;
const inputRentHasta = document.getElementById("rent-hasta") as HTMLInputElement;
const selectPeriodo = document.getElementById("select-periodo") as HTMLSelectElement;

const btnMasVendidos = document.getElementById("btn-mas-vendidos") as HTMLButtonElement;
const btnVentasCategoria = document.getElementById("btn-ventas-categoria") as HTMLButtonElement;
const btnGanancia = document.getElementById("btn-ganancia") as HTMLButtonElement;
const btnMovimientos = document.getElementById("btn-movimientos") as HTMLButtonElement;
const btnResumen = document.getElementById("btn-resumen") as HTMLButtonElement;
const btnRentabilidad = document.getElementById("btn-rentabilidad") as HTMLButtonElement;

// ---------------- Función para inicializar fechas ----------------
function inicializarFechas() {
  const hoy = new Date();
  const haceUnMes = new Date();
  haceUnMes.setMonth(hoy.getMonth() - 1);

  const formatDate = (date: Date) => date.toISOString().split("T")[0];

  inputMasVendidosDesde.value = formatDate(haceUnMes);
  inputMasVendidosHasta.value = formatDate(hoy);

  inputMovDesde.value = formatDate(haceUnMes);
  inputMovHasta.value = formatDate(hoy);

  inputCatDesde.value = formatDate(haceUnMes);
  inputCatHasta.value = formatDate(hoy);

  inputGanDesde.value = formatDate(haceUnMes);
  inputGanHasta.value = formatDate(hoy);

  inputRentDesde.value = formatDate(haceUnMes);
  inputRentHasta.value = formatDate(hoy);
}

// ---------------- FUNCIONES ----------------
async function cargarProductosBajoStock() {
  try {
    const productos: ProductoBajoStock[] = await getProductosBajoStock();
    const tbody = tablaBajoStock.querySelector("tbody")!;
    tbody.innerHTML = "";
    productos.forEach(p => {
      const tr = document.createElement("tr");
      // formatearCantidad acá: el stock viaja como double y arrastra basura de
      // punto flotante (ej "3.4619999999999997") si se muestra crudo.
      tr.innerHTML = `<td>${p.id}</td><td>${esc(p.nombre)}</td><td>${formatearCantidad(p.stock)}</td>`;
      tbody.appendChild(tr);
    });
  } catch (err) {
    console.error(err);
    mostrarToast("Error al cargar productos bajo stock", true);
  }
}

async function cargarProductosMasVendidos() {
  try {
    const desde = inputMasVendidosDesde.value || undefined;
    const hasta = inputMasVendidosHasta.value || undefined;

    const productos: ProductoMasVendido[] = await getProductosMasVendidos(desde, hasta);
    const tbody = tablaMasVendidos.querySelector("tbody")!;
    tbody.innerHTML = "";
    productos.forEach(p => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${p.productoId}</td><td>${esc(p.nombre)}</td><td>${p.cantidadVendida.toFixed(2)}</td>`;
      tbody.appendChild(tr);
    });

    renderMasVendidosChart(productos);
  } catch (err) {
    console.error(err);
    mostrarToast("Error al obtener productos más vendidos", true);
  }
}

function renderMasVendidosChart(productos: ProductoMasVendido[]) {
  const top10 = productos.slice(0, 10);

  chartMasVendidos?.destroy();
  chartMasVendidos = new Chart(canvasMasVendidos, {
    type: "bar",
    data: {
      labels: top10.map(p => p.nombre),
      datasets: [{
        label: "Unidades vendidas",
        data: top10.map(p => p.cantidadVendida),
        backgroundColor: "rgba(2, 136, 209, 0.6)",
        borderColor: "rgba(2, 136, 209, 1)",
        borderWidth: 1
      }]
    },
    options: {
      indexAxis: "y",
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: false } },
      scales: { x: { beginAtZero: true } }
    }
  });
}

async function cargarVentasPorCategoria() {
  try {
    const desde = inputCatDesde.value;
    const hasta = inputCatHasta.value;
    if (!desde || !hasta) {
      mostrarToast("Seleccioná desde y hasta", true);
      return;
    }

    const categorias: VentasPorCategoria[] = await getVentasPorCategoria(desde, hasta);
    const tbody = tablaVentasCategoria.querySelector("tbody")!;
    tbody.innerHTML = "";
    categorias.forEach(c => {
      const tr = document.createElement("tr");
      // DF-SEC-001: categoriaNombre lo escribe quien crea la categoria (puede
      // ser un empleado con PERM_STOCK) y este reporte lo abre el dueño.
      tr.innerHTML = `<td>${esc(c.categoriaNombre)}</td><td>$${c.totalVentas.toLocaleString()}</td><td>${c.cantidadVendida.toFixed(2)}</td>`;
      tbody.appendChild(tr);
    });

    renderVentasPorCategoriaChart(categorias);
  } catch (err) {
    console.error(err);
    mostrarToast("Error al obtener ventas por categoría", true);
  }
}

const PALETA_CATEGORIAS = [
  "#0288d1", "#43a047", "#fbc02d", "#e53935", "#8e24aa",
  "#00897b", "#f4511e", "#3949ab", "#c0ca33", "#6d4c41"
];

function renderVentasPorCategoriaChart(categorias: VentasPorCategoria[]) {
  chartVentasCategoria?.destroy();
  chartVentasCategoria = new Chart(canvasVentasCategoria, {
    type: "doughnut",
    data: {
      labels: categorias.map(c => c.categoriaNombre),
      datasets: [{
        label: "Total vendido ($)",
        data: categorias.map(c => c.totalVentas),
        backgroundColor: categorias.map((_, i) => PALETA_CATEGORIAS[i % PALETA_CATEGORIAS.length]),
        borderColor: "#ffffff",
        borderWidth: 2
      }]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { position: "right" } }
    }
  });
}

async function cargarGanancia() {
  try {
    const desde = inputGanDesde.value;
    const hasta = inputGanHasta.value;
    if (!desde || !hasta) {
      mostrarToast("Seleccioná desde y hasta", true);
      return;
    }

    const datos: GananciaPorFecha[] = await getGananciaPorFecha(desde, hasta);
    const tbody = tablaGanancia.querySelector("tbody")!;
    tbody.innerHTML = "";
    datos.forEach(d => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${d.fecha}</td><td>$${d.totalVentas.toLocaleString()}</td><td>$${d.ganancia.toLocaleString()}</td>`;
      tbody.appendChild(tr);
    });

    renderGananciaChart(datos);
  } catch (err) {
    console.error(err);
    mostrarToast("Error al obtener la ganancia en el tiempo", true);
  }
}

// Fase 8/9 del mapa de cambios: números separados a propósito. No se suman
// "ventas + compras" en un solo total — esa mezcla es justo la confusión
// entre flujo de caja y rentabilidad que esta pantalla tiene que evitar.
async function cargarRentabilidad() {
  try {
    const desde = inputRentDesde.value;
    const hasta = inputRentHasta.value;
    if (!desde || !hasta) {
      mostrarToast("Seleccioná desde y hasta", true);
      return;
    }

    const r: Rentabilidad = await getRentabilidad(desde, hasta);
    const moneda = (n: number) => `$${n.toLocaleString("es-AR", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;

    document.getElementById("rent-ventas")!.textContent = moneda(r.ventasTotales);
    document.getElementById("rent-costo")!.textContent = moneda(r.costoMercaderiaVendida);
    document.getElementById("rent-ganancia-bruta")!.textContent = moneda(r.gananciaBruta);
    document.getElementById("rent-margen")!.textContent = `${r.margenPorcentaje.toFixed(1)}%`;
    document.getElementById("rent-compras")!.textContent = moneda(r.comprasTotales);
    document.getElementById("rent-gastos")!.textContent = moneda(r.gastosTotales);
    document.getElementById("rent-neto")!.textContent = moneda(r.resultadoNeto);

    // Si nadie cargó gastos, el "resultado neto" de arriba no es un dato: es
    // gananciaBruta - 0 disfrazado de neto. Se avisa en vez de mostrarlo
    // como si fuera confiable (ver RentabilidadDTO.gastosCargados).
    document.getElementById("rent-sin-gastos")!.classList.toggle("hidden", r.gastosCargados);

    document.getElementById("rentabilidad-container")!.classList.remove("hidden");
  } catch (err) {
    console.error(err);
    mostrarToast("Error al calcular la rentabilidad", true);
  }
}

function renderGananciaChart(datos: GananciaPorFecha[]) {
  chartGanancia?.destroy();
  chartGanancia = new Chart(canvasGanancia, {
    type: "line",
    data: {
      labels: datos.map(d => d.fecha),
      datasets: [
        {
          label: "Total vendido ($)",
          data: datos.map(d => d.totalVentas),
          borderColor: "rgba(2, 136, 209, 1)",
          backgroundColor: "rgba(2, 136, 209, 0.15)",
          tension: 0.25,
          fill: true
        },
        {
          label: "Ganancia ($)",
          data: datos.map(d => d.ganancia),
          borderColor: "rgba(67, 160, 71, 1)",
          backgroundColor: "rgba(67, 160, 71, 0.15)",
          tension: 0.25,
          fill: true
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: { y: { beginAtZero: true } }
    }
  });
}

async function cargarMovimientos() {
  try {
    const desde = inputMovDesde.value || undefined;
    const hasta = inputMovHasta.value || undefined;

    const movimientos: Movimiento[] = await getMovimientos(desde, hasta);
    const tbody = tablaMovimientos.querySelector("tbody")!;
    tbody.innerHTML = "";
    [...movimientos].reverse().forEach(m => {
      const tr = document.createElement("tr");
      // DF-SEC-001: productoNombre lo escribe quien carga el producto.
      tr.innerHTML = `<td>${m.fecha}</td><td>${esc(m.productoNombre)}</td><td>${m.cantidad.toFixed(2)}</td><td>${m.tipo}</td>`;
      tbody.appendChild(tr);
    });

    renderMovimientosChart(movimientos);
  } catch (err) {
    console.error(err);
    mostrarToast("Error al obtener movimientos", true);
  }
}

function renderMovimientosChart(movimientos: Movimiento[]) {
  const porFecha: Record<string, { compras: number; ventas: number }> = {};
  movimientos.forEach(m => {
    if (!porFecha[m.fecha]) porFecha[m.fecha] = { compras: 0, ventas: 0 };
    if (m.tipo === "COMPRA") porFecha[m.fecha].compras += m.subtotal;
    else porFecha[m.fecha].ventas += m.subtotal;
  });

  const fechas = Object.keys(porFecha).sort();

  chartMovimientos?.destroy();
  chartMovimientos = new Chart(canvasMovimientos, {
    type: "bar",
    data: {
      labels: fechas,
      datasets: [
        {
          label: "Compras ($)",
          data: fechas.map(f => porFecha[f].compras),
          backgroundColor: "rgba(2, 87, 155, 0.6)",
          borderColor: "rgba(2, 87, 155, 1)",
          borderWidth: 1
        },
        {
          label: "Ventas ($)",
          data: fechas.map(f => porFecha[f].ventas),
          backgroundColor: "rgba(46, 125, 50, 0.6)",
          borderColor: "rgba(46, 125, 50, 1)",
          borderWidth: 1
        }
      ]
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      scales: { y: { beginAtZero: true } }
    }
  });
}

async function cargarResumen() {
  try {
    const periodo = selectPeriodo.value as "dia" | "semana" | "mes";
    const resumen: ResumenVentas = await getResumenVentas(periodo);

    // Mostramos los resultados dentro de resumenContainer
    resumenContainer.innerHTML = `
      <p>Total Ventas: $${resumen.totalVentas ?? 0}</p>
      <p>Cantidad Ventas: ${resumen.cantidadVentas ?? 0}</p>
      <p>Ganancia: $${resumen.ganancia ?? 0}</p>
    `;
  } catch (err) {
    console.error(err);
    mostrarToast("Error al cargar resumen", true);
  }
}

const btnImprimirMovimientos = document.getElementById(
  "btn-imprimir-movimientos"
) as HTMLButtonElement;

btnImprimirMovimientos.addEventListener("click", imprimirMovimientos);

function imprimirMovimientos() {
  const tabla = document.getElementById("tabla-movimientos") as HTMLTableElement;
  if (!tabla) return;

  const ventana = window.open("", "_blank");
  if (!ventana) return;

  const desde = inputMovDesde.value || "—";
  const hasta = inputMovHasta.value || "—";
  const ahora = new Date().toLocaleString("es-AR", {
    dateStyle: "long",
    timeStyle: "short"
  });

  const filas = Array.from(tabla.querySelectorAll("tbody tr"))
    .map(tr => {
      const celdas = Array.from(tr.querySelectorAll("td"));
      const tipo = celdas[3]?.textContent?.trim() ?? "";
      const color = tipo === "VENTA" ? "#e8f5e9" : tipo === "COMPRA" ? "#e3f2fd" : "#fff";
      const tipoColor = tipo === "VENTA" ? "#2e7d32" : tipo === "COMPRA" ? "#01579b" : "#37474f";
      return `
        <tr style="background:${color}">
          <td>${celdas[0]?.textContent ?? ""}</td>
          <td>${celdas[1]?.textContent ?? ""}</td>
          <td>${celdas[2]?.textContent ?? ""}</td>
          <td style="color:${tipoColor};font-weight:700">${tipo}</td>
        </tr>`;
    }).join("");

  ventana.document.write(`<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Movimientos — DespensaFamily</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body {
      font-family: 'Segoe UI', Arial, sans-serif;
      background: #fff;
      color: #212529;
      padding: 24px 28px;
      print-color-adjust: exact;
      -webkit-print-color-adjust: exact;
    }

    /* ── ENCABEZADO ── */
    .header {
      display: flex;
      align-items: center;
      gap: 14px;
      padding-bottom: 14px;
      margin-bottom: 20px;
      border-bottom: 3px solid #0288d1;
    }
    .header-logo { font-size: 44px; line-height: 1; }
    .header-empresa { font-size: 20px; font-weight: 800; color: #0288d1; }
    .header-titulo { font-size: 13px; color: #546e7a; margin-top: 2px; font-weight: 500; }
    .header-meta { font-size: 11px; color: #90a4ae; margin-top: 3px; }

    /* ── BANDA DE PERÍODO ── */
    .periodo-bar {
      display: flex;
      gap: 24px;
      background: #e3f2fd;
      border: 1px solid #90caf9;
      border-radius: 8px;
      padding: 10px 16px;
      margin-bottom: 20px;
      font-size: 12px;
      color: #01579b;
    }
    .periodo-bar span { font-weight: 700; }

    /* ── TABLA ── */
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: 11px;
    }
    thead th {
      background: #e3f2fd;
      color: #01579b;
      font-size: 10px;
      font-weight: 700;
      text-transform: uppercase;
      letter-spacing: 0.4px;
      padding: 9px 12px;
      border: 1px solid #b0bec5;
      text-align: left;
    }
    tbody td {
      padding: 7px 12px;
      border-bottom: 1px solid #e0e0e0;
      border-left: 1px solid #eceff1;
      border-right: 1px solid #eceff1;
      color: #37474f;
    }
    tbody tr:last-child td { border-bottom: 1px solid #b0bec5; }

    /* ── PIE ── */
    .footer {
      margin-top: 24px;
      display: flex;
      justify-content: space-between;
      font-size: 10px;
      color: #90a4ae;
      border-top: 1px solid #e0e0e0;
      padding-top: 8px;
    }

    @media print {
      @page { margin: 15mm 12mm; size: A4 portrait; }
      body { padding: 0; }
      tr { page-break-inside: avoid; }
    }
  </style>
</head>
<body>

  <div class="header">
    <div class="header-logo">🏪</div>
    <div>
      <div class="header-empresa">DespensaFamily</div>
      <div class="header-titulo">Reporte de Movimientos de Stock</div>
      <div class="header-meta">Impreso el ${ahora}</div>
    </div>
  </div>

  <div class="periodo-bar">
    <div>Período: <span>${desde}</span> al <span>${hasta}</span></div>
    <div>Total registros: <span>${tabla.querySelectorAll("tbody tr").length}</span></div>
  </div>

  <table>
    <thead>
      <tr>
        <th>Fecha</th>
        <th>Producto</th>
        <th>Cantidad</th>
        <th>Tipo</th>
      </tr>
    </thead>
    <tbody>${filas}</tbody>
  </table>

  <div class="footer">
    <span>DespensaFamily — Control de Stock</span>
    <span>${ahora}</span>
  </div>

</body>
</html>`);

  ventana.document.close();
  ventana.print();
}


// ---------------- EVENTOS ----------------
btnMasVendidos.addEventListener("click", cargarProductosMasVendidos);
btnVentasCategoria.addEventListener("click", cargarVentasPorCategoria);
btnGanancia.addEventListener("click", cargarGanancia);
btnRentabilidad.addEventListener("click", cargarRentabilidad);
btnMovimientos.addEventListener("click", cargarMovimientos);
btnResumen.addEventListener("click", cargarResumen);
const btnImprimir = document.getElementById("btn-imprimir") as HTMLButtonElement;

btnImprimir.addEventListener("click", () => {
  window.print();
});

// ---------------- INIT ----------------
const printFecha = document.getElementById("print-fecha");
if (printFecha) {
  printFecha.textContent = new Date().toLocaleString("es-AR", { dateStyle: "long", timeStyle: "short" });
}

inicializarFechas();
cargarProductosBajoStock();
cargarProductosMasVendidos();
cargarVentasPorCategoria();
cargarGanancia();
cargarMovimientos();
cargarResumen();
cargarRentabilidad();

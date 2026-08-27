import { checkAdmin } from "../../services/auth.service";
checkAdmin();

import type { ProductoBajoStock, Movimiento, ProductoMasVendido, ResumenVentas } from "../../models/Reportes";
import {
  getProductosBajoStock,
  getProductosMasVendidos,
  getMovimientos,
  getResumenVentas
} from "../../services/reportes.service";
import Chart from "chart.js/auto";

// ---------------- DOM ----------------
const tablaBajoStock = document.getElementById("tabla-bajo-stock") as HTMLTableElement;
const tablaMasVendidos = document.getElementById("tabla-mas-vendidos") as HTMLTableElement;
const tablaMovimientos = document.getElementById("tabla-movimientos") as HTMLTableElement;
const resumenContainer = document.getElementById("resumen-container") as HTMLDivElement;

const canvasMasVendidos = document.getElementById("chart-mas-vendidos") as HTMLCanvasElement;
const canvasMovimientos = document.getElementById("chart-movimientos") as HTMLCanvasElement;

let chartMasVendidos: Chart | null = null;
let chartMovimientos: Chart | null = null;

const inputMasVendidosDesde = document.getElementById("fecha-desde") as HTMLInputElement;
const inputMasVendidosHasta = document.getElementById("fecha-hasta") as HTMLInputElement;
const inputMovDesde = document.getElementById("mov-desde") as HTMLInputElement;
const inputMovHasta = document.getElementById("mov-hasta") as HTMLInputElement;
const selectPeriodo = document.getElementById("select-periodo") as HTMLSelectElement;

const btnMasVendidos = document.getElementById("btn-mas-vendidos") as HTMLButtonElement;
const btnMovimientos = document.getElementById("btn-movimientos") as HTMLButtonElement;
const btnResumen = document.getElementById("btn-resumen") as HTMLButtonElement;

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
}

// ---------------- FUNCIONES ----------------
async function cargarProductosBajoStock() {
  try {
    const productos: ProductoBajoStock[] = await getProductosBajoStock();
    const tbody = tablaBajoStock.querySelector("tbody")!;
    tbody.innerHTML = "";
    productos.forEach(p => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${p.id}</td><td>${p.nombre}</td><td>${p.stock}</td>`;
      tbody.appendChild(tr);
    });
  } catch (err) {
    console.error(err);
    alert("Error al cargar productos bajo stock");
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
      tr.innerHTML = `<td>${p.productoId}</td><td>${p.nombre}</td><td>${p.cantidadVendida}</td>`;
      tbody.appendChild(tr);
    });

    renderMasVendidosChart(productos);
  } catch (err) {
    console.error(err);
    alert("Error al obtener productos más vendidos");
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

async function cargarMovimientos() {
  try {
    const desde = inputMovDesde.value || undefined;
    const hasta = inputMovHasta.value || undefined;

    const movimientos: Movimiento[] = await getMovimientos(desde, hasta);
    const tbody = tablaMovimientos.querySelector("tbody")!;
    tbody.innerHTML = "";
    [...movimientos].reverse().forEach(m => {
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${m.fecha}</td><td>${m.productoNombre}</td><td>${m.cantidad}</td><td>${m.tipo}</td>`;
      tbody.appendChild(tr);
    });

    renderMovimientosChart(movimientos);
  } catch (err) {
    console.error(err);
    alert("Error al obtener movimientos");
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
    alert("Error al cargar resumen");
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
cargarMovimientos();
cargarResumen();

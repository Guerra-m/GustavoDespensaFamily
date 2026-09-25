import { esc, formatearFechaHora, formatearMoneda } from "../../utils/format";
import { checkAdmin } from "../../services/auth.service";
checkAdmin();

import { getVentas } from "../../services/ventas.service";
import { getCompras, getCompraDetalle } from "../../services/compras.service";
import { getProductos } from "../../services/productos.service";
import { getProductosMasVendidos, getResumenVentas, getMovimientos, getProductosBajoStock } from "../../services/reportes.service";
import Chart from "chart.js/auto";

// ---------------- DOM ----------------
const toast = document.getElementById("toast") as HTMLDivElement | null;

/** DF-UX-003/DF-UX-008: aviso visible ante un error -- antes home.ts solo
 * hacia console.error, y quien mira la pantalla no tiene consola abierta. */
function mostrarToast(mensaje: string, error = true) {
  if (!toast) return;
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  toast.classList.toggle("error", error);
  setTimeout(() => toast.classList.add("hidden"), 3000);
}

const kpiVentas = document.getElementById("kpi-ventas") as HTMLParagraphElement;
const kpiCompras = document.getElementById("kpi-compras") as HTMLParagraphElement;
const kpiStockBajo = document.getElementById("kpi-stock-bajo") as HTMLParagraphElement;
const kpiCardStockBajo = document.getElementById("kpi-card-stock-bajo") as HTMLAnchorElement;
const kpiProductos = document.getElementById("kpi-productos") as HTMLParagraphElement;

const tablaUltimasVentas = document.querySelector("#tabla-ultimas-ventas tbody") as HTMLTableSectionElement;
const tablaUltimasCompras = document.querySelector("#tabla-ultimas-compras tbody") as HTMLTableSectionElement;

const canvasVentas = document.getElementById("ventas-chart") as HTMLCanvasElement;
const canvasTopProductos = document.getElementById("top-productos-chart") as HTMLCanvasElement;

// ---------------- ESTADO ----------------
let productos: any[] = [];
let ventas: any[] = [];
let compras: any[] = [];

// ---------------- FUNCIONES ----------------
function getHoyLocal(): string {
  const hoy = new Date();
  return `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, "0")}-${String(hoy.getDate()).padStart(2, "0")}`;
}

async function cargarKPIs() {
  try {
    productos = await getProductos();
    ventas = await getVentas();
    compras = await getCompras();

    const hoyStr = getHoyLocal();

    // Ventas hoy
    const resumenHoy = await getResumenVentas("dia");
    kpiVentas.textContent = formatearMoneda(resumenHoy.totalVentas);

    // Compras registradas hoy: TODAS las activas (pagadas o no) -- distinto
    // de "Compras pagadas" de Rentabilidad, que solo cuenta lo que salio de
    // caja de verdad (DF-UX-010).
    const movimientosComprasHoy = await getMovimientos(hoyStr, hoyStr, "COMPRA");
    const totalComprasHoy = movimientosComprasHoy.reduce((acc, m) => acc + m.subtotal, 0);
    kpiCompras.textContent = formatearMoneda(totalComprasHoy);

    // Stock bajo: se resalta la tarjeta en naranja solo cuando hay algo
    // que atender — si no, compite visualmente con métricas que no piden
    // ninguna acción.
    const bajoStock = await getProductosBajoStock();
    kpiStockBajo.textContent = `${bajoStock.length}`;
    kpiCardStockBajo.classList.toggle("kpi-card--alerta", bajoStock.length > 0);

    // Productos totales
    kpiProductos.textContent = `${productos.length}`;
  } catch (err) {
    console.error(err);
    kpiVentas.textContent = "Error";
    kpiCompras.textContent = "Error";
    kpiStockBajo.textContent = "Error";
    kpiProductos.textContent = "Error";
    mostrarToast("No se pudieron cargar los indicadores del inicio");
  }
}

// Cargar últimas ventas y compras
async function cargarUltimas() {
  try {
    // Últimas 5 ventas
    const ultimasVentas = ventas.slice(-5).reverse();
    tablaUltimasVentas.innerHTML = "";
    ultimasVentas.forEach(v => {
      const tr = document.createElement("tr");
      if (v.estado === "ANULADA") tr.classList.add("venta-anulada");
      tr.innerHTML = `<td>${v.id}</td><td>${formatearFechaHora(v.fechaHora)}</td><td>${formatearMoneda(v.total)}</td>`;
      tablaUltimasVentas.appendChild(tr);
    });

    // Últimas 5 compras
    const ultimasCompras = compras.slice(-5).reverse();
    tablaUltimasCompras.innerHTML = "";
    for (const c of ultimasCompras) {
      const detalle = await getCompraDetalle(c.id!);
      const total = detalle.detalles.reduce((acc, d) => acc + d.cantidad * d.precioCosto, 0);
      const tr = document.createElement("tr");
      tr.innerHTML = `<td>${c.id}</td><td>${esc(c.proveedor)}</td><td>${formatearMoneda(total)}</td>`;
      tablaUltimasCompras.appendChild(tr);
    }
  } catch (err) {
    console.error(err);
    mostrarToast("No se pudieron cargar las últimas ventas y compras");
  }
}

// Gráfico de ventas últimos 7 días
function renderVentasChart() {
  const diasMap: Record<string, number> = {};
  for (let i = 6; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`;
    diasMap[key] = 0;
  }

  // Las anuladas ahora vienen en el listado (antes el backend las filtraba):
  // hay que descartarlas acá o el gráfico contaría plata que se devolvió.
  ventas.filter(v => v.estado !== "ANULADA").forEach(v => {
    const fecha = v.fechaHora.split("T")[0];
    if (diasMap[fecha] !== undefined) diasMap[fecha] += v.total;
  });

  const labels = Object.keys(diasMap);
  const data = Object.values(diasMap);

  new Chart(canvasVentas, {
    type: 'bar',
    data: {
      labels,
      datasets: [{
        label: 'Ventas ($)',
        data,
        backgroundColor: 'rgba(33, 150, 243, 0.6)',
        borderColor: 'rgba(33, 150, 243, 1)',
        borderWidth: 1
      }]
    },
    options: {
      responsive: true,
      scales: { y: { beginAtZero: true } }
    }
  });
}

function formatFechaLocal(fecha: Date): string {
  return `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, "0")}-${String(fecha.getDate()).padStart(2, "0")}`;
}

// Gráfico de top productos vendidos (últimos 30 días)
async function renderTopProductosChart() {
  try {
    const hoy = new Date();
    const hace30Dias = new Date();
    hace30Dias.setDate(hoy.getDate() - 30);

    const top = await getProductosMasVendidos(formatFechaLocal(hace30Dias), formatFechaLocal(hoy));
    const top5 = top.slice(0, 5);

    new Chart(canvasTopProductos, {
      type: 'bar',
      data: {
        labels: top5.map(p => p.nombre),
        datasets: [{
          label: 'Unidades vendidas',
          data: top5.map(p => p.cantidadVendida),
          backgroundColor: 'rgba(2, 136, 209, 0.6)',
          borderColor: 'rgba(2, 136, 209, 1)',
          borderWidth: 1
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { x: { beginAtZero: true } }
      }
    });
  } catch (err) {
    console.error(err);
    mostrarToast("No se pudo cargar el top de productos más vendidos");
  }
}

// ---------------- INIT ----------------
async function initHome() {
  await cargarKPIs();
  await cargarUltimas();
  try {
    renderVentasChart();
  } catch (err) {
    console.error(err);
    mostrarToast("No se pudo dibujar el gráfico de ventas");
  }
  await renderTopProductosChart();
}

initHome();

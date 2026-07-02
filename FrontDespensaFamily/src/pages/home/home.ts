import { checkAdmin } from "../../services/auth.service";
checkAdmin();

import { getVentas } from "../../services/ventas.service";
import { getCompras, getCompraDetalle } from "../../services/compras.service";
import { getProductos } from "../../services/productos.service";
import Chart from "chart.js/auto";

// ---------------- DOM ----------------
const kpiVentas = document.getElementById("kpi-ventas") as HTMLParagraphElement;
const kpiCompras = document.getElementById("kpi-compras") as HTMLParagraphElement;
const kpiStockBajo = document.getElementById("kpi-stock-bajo") as HTMLParagraphElement;
const kpiProductos = document.getElementById("kpi-productos") as HTMLParagraphElement;

const tablaUltimasVentas = document.querySelector("#tabla-ultimas-ventas tbody") as HTMLTableSectionElement;
const tablaUltimasCompras = document.querySelector("#tabla-ultimas-compras tbody") as HTMLTableSectionElement;

const canvasVentas = document.getElementById("ventas-chart") as HTMLCanvasElement;

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
    const ventasHoy = ventas.filter(v => v.fechaHora.split("T")[0] === hoyStr);
    const totalVentasHoy = ventasHoy.reduce((acc, v) => acc + v.total, 0);
    kpiVentas.textContent = `$${totalVentasHoy.toLocaleString()}`;

    // Compras hoy
    const comprasHoy = compras.filter(c => c.fechaHora.split("T")[0] === hoyStr);
    let totalComprasHoy = 0;
    for (const c of comprasHoy) {
      const detalle = await getCompraDetalle(c.id!);
      totalComprasHoy += detalle.detalles.reduce((acc, d) => acc + d.cantidad * d.precioCosto, 0);
    }
    kpiCompras.textContent = `$${totalComprasHoy.toLocaleString()}`;

    // Stock bajo
    const bajoStock = productos.filter(p => p.stock <= 5);
    kpiStockBajo.textContent = `${bajoStock.length}`;

    // Productos totales
    kpiProductos.textContent = `${productos.length}`;
  } catch (err) {
    console.error(err);
    kpiVentas.textContent = "Error";
    kpiCompras.textContent = "Error";
    kpiStockBajo.textContent = "Error";
    kpiProductos.textContent = "Error";
  }
}

// Cargar últimas ventas y compras
async function cargarUltimas() {
  // Últimas 5 ventas
  const ultimasVentas = ventas.slice(-5).reverse();
  tablaUltimasVentas.innerHTML = "";
  ultimasVentas.forEach(v => {
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>${v.id}</td><td>${v.fechaHora}</td><td>$${v.total.toLocaleString()}</td>`;
    tablaUltimasVentas.appendChild(tr);
  });

  // Últimas 5 compras
  const ultimasCompras = compras.slice(-5).reverse();
  tablaUltimasCompras.innerHTML = "";
  for (const c of ultimasCompras) {
    const detalle = await getCompraDetalle(c.id!);
    const total = detalle.detalles.reduce((acc, d) => acc + d.cantidad * d.precioCosto, 0);
    const tr = document.createElement("tr");
    tr.innerHTML = `<td>${c.id}</td><td>${c.proveedor}</td><td>$${total.toLocaleString()}</td>`;
    tablaUltimasCompras.appendChild(tr);
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

  ventas.forEach(v => {
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

// ---------------- INIT ----------------
async function initHome() {
  await cargarKPIs();
  await cargarUltimas();
  renderVentasChart();
}

initHome();

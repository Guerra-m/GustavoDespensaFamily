import type { ProductoBajoStock, Movimiento, ProductoMasVendido, ResumenVentas } from "../../models/Reportes";
import {
  getProductosBajoStock,
  getProductosMasVendidos,
  getMovimientos,
  getResumenVentas
} from "../../services/reportes.service";

// ---------------- DOM ----------------
const tablaBajoStock = document.getElementById("tabla-bajo-stock") as HTMLTableElement;
const tablaMasVendidos = document.getElementById("tabla-mas-vendidos") as HTMLTableElement;
const tablaMovimientos = document.getElementById("tabla-movimientos") as HTMLTableElement;
const resumenContainer = document.getElementById("resumen-container") as HTMLDivElement;

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
  } catch (err) {
    console.error(err);
    alert("Error al obtener productos más vendidos");
  }
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
  } catch (err) {
    console.error(err);
    alert("Error al obtener movimientos");
  }
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

  ventana.document.write(`
    <html>
      <head>
        <title>Reporte de Movimientos</title>
        <style>
          body {
            font-family: Arial, sans-serif;
            padding: 20px;
          }

          h2 {
            text-align: center;
            margin-bottom: 20px;
          }

          table {
            width: 100%;
            border-collapse: collapse;
          }

          th, td {
            border: 1px solid #000;
            padding: 8px;
            text-align: left;
            font-size: 12px;
          }

          th {
            background-color: #f0f0f0;
          }

          tr {
            page-break-inside: avoid;
          }
        </style>
      </head>
      <body>
        <h2>Movimientos de Stock</h2>
        ${tabla.outerHTML}
      </body>
    </html>
  `);

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
inicializarFechas();
cargarProductosBajoStock();
cargarProductosMasVendidos();
cargarMovimientos();
cargarResumen();

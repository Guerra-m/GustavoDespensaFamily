import { checkAuth } from "../../services/auth.service";
checkAuth();

import { getProductos } from "../../services/productos.service";
import { getVentas, createVenta, getDetalleVenta, anularVenta } from "../../services/ventas.service";
import { getCategorias } from "../../services/categorias.service";
import type { Categoria } from "../../models/Categoria";
import type { Producto } from "../../models/Producto";
import type { DetalleVentaRequest } from "../../models/DetalleVentaRequest";
import type { Venta } from "../../models/Venta";
import { Html5Qrcode } from "html5-qrcode";

/* =======================
   ELEMENTOS DEL DOM
======================= */
const inputBuscarProducto = document.getElementById("input-buscar-producto") as HTMLInputElement;
const inputCantidad = document.getElementById("input-cantidad") as HTMLInputElement;
const btnAgregar = document.getElementById("btn-agregar") as HTMLButtonElement;
const btnConfirmar = document.getElementById("btn-confirmar") as HTMLButtonElement;

const tablaDetalle = document.getElementById("tabla-detalle") as HTMLTableSectionElement;
const tablaVentas = document.getElementById("tabla-ventas") as HTMLTableSectionElement;
const spanTotal = document.getElementById("total") as HTMLSpanElement;

const modalDetalle = document.getElementById("modal-detalle") as HTMLDivElement;
const tablaDetalleVenta = document.getElementById("tabla-detalle-venta") as HTMLTableSectionElement;
const detalleVentaId = document.getElementById("detalle-venta-id") as HTMLSpanElement;
const detalleTotal = document.getElementById("detalle-total") as HTMLSpanElement;
const btnCerrarDetalle = document.getElementById("btn-cerrar-detalle") as HTMLButtonElement;

const btnScanCamara = document.getElementById("btn-scan-camara") as HTMLButtonElement;
const modalScan = document.getElementById("modal-scan") as HTMLDivElement;
const btnCerrarScan = document.getElementById("btn-cerrar-scan") as HTMLButtonElement;

const productosSection = document.getElementById("productos-section") as HTMLDivElement;
const productosGrid = document.getElementById("productos-grid") as HTMLDivElement;
const categoriasSection = document.getElementById("categorias-section") as HTMLDivElement;

const toast = document.getElementById("toast") as HTMLDivElement;
const selectCliente = document.getElementById("select-cliente") as HTMLSelectElement;
/* =======================
   ESTADO
======================= */
let productos: Producto[] = [];
let categorias: Categoria[] = [];
let carrito: DetalleVentaRequest[] = [];
let html5QrCode: Html5Qrcode;
let escaneando = false;
let ultimoCodigoEscaneado: string | null = null;
let productoSeleccionado: Producto | null = null;

function esProductoPesable(producto: Producto): boolean {
  return producto.tipoCantidad === "PESO";
}

function unidadProducto(producto: Producto): "u" | "kg" {
  return producto.unidadMedida || (esProductoPesable(producto) ? "kg" : "u");
}

function fraccionMinima(producto: Producto): number {
  if (producto.fraccionMinima && producto.fraccionMinima > 0) return producto.fraccionMinima;
  return esProductoPesable(producto) ? 0.001 : 1;
}

function configurarInputCantidad(producto: Producto) {
  const step = fraccionMinima(producto);
  inputCantidad.step = step.toString();
  inputCantidad.min = step.toString();
  inputCantidad.placeholder = esProductoPesable(producto)
    ? `Cantidad en ${unidadProducto(producto)}`
    : "Cantidad en unidades";
  inputCantidad.value = step.toString();
}

function formatearCantidad(cantidad: number, producto?: Producto) {
  if (!producto) return cantidad.toString();
  if (esProductoPesable(producto)) return `${cantidad.toFixed(3)} ${unidadProducto(producto)}`;
  return `${cantidad} ${unidadProducto(producto)}`;
}

/* =======================
   CARGA DE DATOS
======================= */
async function cargarProductos() {
  [productos, categorias] = await Promise.all([getProductos(), getCategorias()]);
  productos.sort((a, b) => a.nombre.localeCompare(b.nombre));
  renderProductosGrid();
  renderCategoriasSection();
}

async function cargarVentas() {
  const ventas = await getVentas();
  tablaVentas.innerHTML = "";

  [...ventas].reverse().forEach(v => {
    if (!v.id) return;

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${v.id}</td>
      <td>${v.fechaHora}</td>
      <td>$${v.total}</td>
      <td>
        <button class="btn-imprimir">Imprimir</button>
        <button class="btn-anular">Anular</button>
      </td>
    `;

    tr.addEventListener("click", () => abrirDetalleVenta(v.id!));

    const btnAnular = tr.querySelector(".btn-anular") as HTMLButtonElement;
    btnAnular.addEventListener("click", async (e) => {
      e.stopPropagation();
      if (confirm(`¿Seguro que desea anular la venta #${v.id}?`)) {
        await anularVenta(v.id!);
        cargarVentas();
      }
    });

    const btnImprimir = tr.querySelector(".btn-imprimir") as HTMLButtonElement;
    btnImprimir.addEventListener("click", async (e) => {
      e.stopPropagation();
      await imprimirTicket(v);
    });

    tablaVentas.appendChild(tr);
  });
}

/* =======================
   RENDER GRID DE PRODUCTOS
======================= */
function crearProductoCard(prod: Producto): HTMLDivElement {
  const card = document.createElement("div");
  card.className = "producto-card";
  card.dataset.productoId = String(prod.id);
  if (prod.stock < 10) card.classList.add("stock-bajo");
  if (productoSeleccionado?.id === prod.id) card.classList.add("seleccionado");

  card.innerHTML = `
    <div class="producto-imagen">
      ${prod.imagenUrl ? `<img src="${prod.imagenUrl}" alt="${prod.nombre}">` : ""}
    </div>
    <div class="producto-info">
      <div class="producto-nombre">${prod.nombre}</div>
      <div class="producto-precio">$${prod.precioVenta.toFixed(2)} / ${unidadProducto(prod)}</div>
      <div class="producto-stock">Stock: ${prod.stock} ${unidadProducto(prod)}</div>
    </div>
  `;
  card.addEventListener("click", () => seleccionarProducto(prod));
  return card;
}

function renderProductosGrid(filtro: string = "") {
  productosGrid.innerHTML = "";

  const textoFiltro = filtro.toLowerCase().trim();
  const productosFiltrados = textoFiltro
    ? productos.filter(p =>
        p.nombre.toLowerCase().includes(textoFiltro) ||
        p.codigoBarras?.toLowerCase().includes(textoFiltro)
      )
    : productos;

  if (productosFiltrados.length === 0) {
    productosGrid.innerHTML = `<div class="sin-resultados">No se encontraron productos</div>`;
    return;
  }

  productosFiltrados.forEach(prod => productosGrid.appendChild(crearProductoCard(prod)));
}

function renderCategoriasSection() {
  categoriasSection.innerHTML = "";

  categorias.forEach(cat => {
    const prods = productos.filter(p => p.categoriaId === cat.id);
    if (prods.length === 0) return;

    const bloque = document.createElement("div");
    bloque.className = "categoria-bloque";

    const titulo = document.createElement("h2");
    titulo.className = "categoria-bloque-titulo";
    titulo.textContent = cat.nombre;
    bloque.appendChild(titulo);

    const grid = document.createElement("div");
    grid.className = "categoria-grid";
    prods.forEach(prod => grid.appendChild(crearProductoCard(prod)));
    bloque.appendChild(grid);

    categoriasSection.appendChild(bloque);
  });

  const sinCat = productos.filter(p => !categorias.some(c => c.id === p.categoriaId));
  if (sinCat.length > 0) {
    const bloque = document.createElement("div");
    bloque.className = "categoria-bloque";
    const titulo = document.createElement("h2");
    titulo.className = "categoria-bloque-titulo";
    titulo.textContent = "Sin categoría";
    bloque.appendChild(titulo);
    const grid = document.createElement("div");
    grid.className = "categoria-grid";
    sinCat.forEach(prod => grid.appendChild(crearProductoCard(prod)));
    bloque.appendChild(grid);
    categoriasSection.appendChild(bloque);
  }
}

function seleccionarProducto(producto: Producto) {
  productoSeleccionado = producto;
  inputBuscarProducto.value = producto.nombre;
  inputBuscarProducto.dataset.id = producto.id!.toString();
  configurarInputCantidad(producto);
  inputCantidad.focus();

  document.querySelectorAll<HTMLDivElement>(".producto-card").forEach(card => {
    card.classList.toggle("seleccionado", card.dataset.productoId === String(producto.id));
  });

  mostrarToast(`Producto seleccionado: ${producto.nombre}`);
}

/* =======================
   CARRITO
======================= */
function renderCarrito() {
  tablaDetalle.innerHTML = "";
  let total = 0;

  carrito.forEach((item, index) => {
    const prod = productos.find(p => p.id === item.productoId)!;
    const subtotal = prod.precioVenta * item.cantidad;
    total += subtotal;

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${prod.nombre}</td>
      <td>${formatearCantidad(item.cantidad, prod)}</td>
      <td>$${prod.precioVenta.toFixed(2)} / ${unidadProducto(prod)}</td>
      <td>$${subtotal.toFixed(2)}</td>
      <td><button class="btn-quitar" data-index="${index}">Quitar</button></td>
    `;
    tablaDetalle.appendChild(tr);
  });

  spanTotal.textContent = total.toFixed(2);

  document.querySelectorAll(".btn-quitar").forEach(btn => {
    btn.addEventListener("click", () => {
      const index = Number((btn as HTMLElement).dataset.index);
      carrito.splice(index, 1);
      renderCarrito();
    });
  });
}

/* =======================
   FUNCIONES CLAVE
======================= */

async function procesarCodigoEscaneado(codigo: string) {
  codigo = codigo.trim();

  if (!codigo) return;

  if (!escaneando) escaneando = true;

  if (!productos.length) await cargarProductos();

  const producto = productos.find(p => String(p.codigoBarras) === codigo);

  if (!producto) {
    mostrarToast("Producto no encontrado", true);
    return;
  }

  mostrarToast(`Producto encontrado: ${producto.nombre}`);
  seleccionarProducto(producto);

  await cerrarScanner();
}

async function abrirDetalleVenta(ventaId: number) {
  const ventaDetalle = await getDetalleVenta(ventaId);
  if (!ventaDetalle?.detalles) return;

  detalleVentaId.textContent = ventaId.toString();
  tablaDetalleVenta.innerHTML = "";
  let total = 0;

  ventaDetalle.detalles.forEach(item => {
    const producto = productos.find(p => p.id === item.productoId);
    const nombre = producto ? producto.nombre : "Producto eliminado";

    const tr = document.createElement("tr");
    tr.innerHTML = `<td>${nombre}</td><td>${formatearCantidad(item.cantidad, producto)}</td><td>$${item.precioUnitario.toFixed(2)} / ${producto ? unidadProducto(producto) : "u"}</td><td>$${item.subtotal.toFixed(2)}</td>`;
    tablaDetalleVenta.appendChild(tr);
    total += item.subtotal;
  });

  detalleTotal.textContent = total.toFixed(2);
  modalDetalle.classList.remove("hidden");
}

function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  toast.classList.toggle("error", error);
  setTimeout(() => toast.classList.add("hidden"), 2000);
}
function obtenerCliente(): string {
  if (!selectCliente) return "Consumidor Final";
  return selectCliente.options[selectCliente.selectedIndex].text;
}

/* =======================
   SCANNER
======================= */
async function abrirScanner() {
  modalScan.classList.remove("hidden");
  const reader = document.getElementById("reader")!;
  reader.innerHTML = "";

  if (html5QrCode) {
    try { await html5QrCode.stop(); await html5QrCode.clear(); } catch {}
    html5QrCode = null as any;
  }

  ultimoCodigoEscaneado = null;
  escaneando = true;

  html5QrCode = new Html5Qrcode("reader");
  await html5QrCode.start(
    { facingMode: "environment" },
    { fps: 15, qrbox: { width: 250, height: 150 }, aspectRatio: 1.77778 },
    (codigo) => {
      procesarCodigoEscaneado(codigo);
    },
    () => {}
  );
}

async function cerrarScanner() {
  escaneando = false;
  ultimoCodigoEscaneado = null;

  if (html5QrCode) {
    try { await html5QrCode.stop(); await html5QrCode.clear(); } catch {}
    html5QrCode = null as any;
  }

  const reader = document.getElementById("reader")!;
  reader.innerHTML = "";
  modalScan.classList.add("hidden");
}

/* =======================
   EVENTOS
======================= */
btnAgregar.addEventListener("click", () => {
  const productoId = Number(inputBuscarProducto.dataset.id);
  const cantidad = Number(inputCantidad.value);
  if (!productoId || cantidad <= 0) return alert("Seleccione un producto válido y cantidad");

  const producto = productos.find(p => p.id === productoId);
  if (!producto) return;
  if (!esProductoPesable(producto) && !Number.isInteger(cantidad)) {
    return alert("Este producto se vende por unidad, la cantidad debe ser entera");
  }

  carrito.push({ productoId, cantidad });
  renderCarrito();

  inputBuscarProducto.value = "";
  inputCantidad.value = "";
  inputCantidad.step = "0.001";
  inputCantidad.min = "0.001";
  inputCantidad.placeholder = "Cantidad (u o kg)";
  delete inputBuscarProducto.dataset.id;
  productoSeleccionado = null;
  renderProductosGrid();
  inputBuscarProducto.focus();
  
  mostrarToast("Producto agregado al carrito");
});

btnConfirmar.addEventListener("click", async () => {
  if (!carrito.length) return alert("No hay productos en la venta");

  const ventaCreada = await createVenta({ detalles: carrito });

  carrito = [];
  renderCarrito();
  cargarVentas();

  await imprimirTicket(ventaCreada);
  
  mostrarToast("Venta registrada exitosamente");
});

btnCerrarDetalle.addEventListener("click", () => modalDetalle.classList.add("hidden"));

inputBuscarProducto.addEventListener("keydown", async (e) => {
  if (e.key !== "Enter") return;
  e.preventDefault();

  const codigo = inputBuscarProducto.value.trim();

  if (!inputBuscarProducto.dataset.id) {
    await procesarCodigoEscaneado(codigo);
  }

  if (inputBuscarProducto.dataset.id) {
    btnAgregar.click();
  } else {
    mostrarToast("Producto no encontrado", true);
  }
});

// btnScanCamara.addEventListener("click", abrirScanner);
// btnCerrarScan.addEventListener("click", cerrarScanner);

// Filtrar productos en tiempo real
inputBuscarProducto.addEventListener("input", () => {
  const texto = inputBuscarProducto.value;
  renderProductosGrid(texto);
});

/* =======================
   INIT
======================= */
cargarProductos();
cargarVentas();
setTimeout(() => inputBuscarProducto.focus(), 300);

async function imprimirTicket(venta: Venta) {
  if (!productos.length) await cargarProductos();

  const response = await getDetalleVenta(venta.id!);
  const nroTicket = formatearNroTicket(venta.id!);
  const detalles = response.detalles;

  let filas = "";

  detalles.forEach(d => {
    const producto = productos.find(p => p.id === d.productoId);
    const nombre = producto ? producto.nombre : "Producto eliminado";

    filas += `
      <tr>
        <td>${nombre}</td>
        <td style="text-align:center">${formatearCantidad(d.cantidad, producto)}</td>
        <td style="text-align:right">$${d.precioUnitario.toFixed(2)} / ${producto ? unidadProducto(producto) : "u"}</td>
        <td style="text-align:right">$${d.subtotal.toFixed(2)}</td>
      </tr>
    `;
  });

 const ticketHtml = `
<html>
  <head>
    <title>Ticket Venta #${venta.id}</title>

    <style>
      @page {
        size: 80mm auto;
        margin: 0;
      }

      body {
        width: 65mm;
        margin: 0;
        padding: 2mm;
        font-family: monospace;
        font-size: 10px;
        letter-spacing: -0.2px;
      }

      h2, p {
        margin: 2px 0;
      }

      h2 {
        text-align: center;
      }

      .center {
        text-align: center;
      }

      .row {
        display: flex;
        justify-content: space-between;
        margin: 2px 0;
      }

      .line {
        border-top: 1px dashed #000;
        margin: 4px 0;
      }

      table {
        width: 100%;
        border-collapse: collapse;
        table-layout: fixed;
        font-size: 10px;
      }

      th, td {
        padding: 1px 0;
      }

      th {
        border-bottom: 1px dashed #000;
      }

      /* Producto (más chico) */
th:nth-child(1),
td:nth-child(1) {
  width: 45%;
  word-break: break-word;
}

/* Cant (más pegado al producto) */
th:nth-child(2),
td:nth-child(2) {
  width: 10%;
  text-align: left;
}

/* Precio */
th:nth-child(3),
td:nth-child(3) {
  width: 20%;
  text-align: right;
  padding-left: 4px;
}

/* Subtotal (más separado) */
th:nth-child(4),
td:nth-child(4) {
  width: 25%;
  text-align: right;
  padding-left: 6px;
}

      /* Números más legibles */
      td:nth-child(2),
      td:nth-child(3),
      td:nth-child(4) {
        font-size: 10px;
      }

      .total {
        font-size: 12px;
        font-weight: bold;
        text-align: right;
        margin-top: 4px;
      }
    </style>
  </head>

  <body>

    <h2>Despensa Family</h2>
    <p class="center">Ticket de Venta</p>

    <div class="line"></div>

    <div class="row">
      <span>Fecha: ${venta.fechaHora}</span>
      <span>Nº: ${nroTicket}</span>
    </div>

    <div class="line"></div>

    <p>Cliente: ${obtenerCliente()}</p>

    <div class="line"></div>

    <table>
      <thead>
        <tr>
          <th style="text-align:left">Prod</th>
          <th>Cant</th>
          <th>Precio</th>
          <th>Subt</th>
        </tr>
      </thead>
      <tbody>
        ${filas}
      </tbody>
    </table>

    <div class="line"></div>

    <p class="total">TOTAL: $${venta.total.toFixed(2)}</p>

    <div class="line"></div>

    <p class="center">Gracias por su compra</p>

    <div class="line"></div>

    <p class="center" style="font-size:9px;">
      Ticket no válido como factura
    </p>

    <script>
      window.print();
      window.onafterprint = () => window.close();
    </script>

  </body>
</html>
`;

  const ventana = window.open("", "_blank", "width=350,height=600");
  ventana!.document.write(ticketHtml);
  ventana!.document.close();
}

function formatearNroTicket(id: number): string {
  return id.toString().padStart(8, "0");
}
function obtenerFechaActual() {
  const hoy = new Date();

  const dia = String(hoy.getDate()).padStart(2, '0');
  const mes = String(hoy.getMonth() + 1).padStart(2, '0');
  const anio = hoy.getFullYear();

  return `${dia}/${mes}/${anio}`;
}

const fechaElement = document.getElementById("fecha");

if (fechaElement) {
  fechaElement.textContent = "Fecha: " + obtenerFechaActual();
}

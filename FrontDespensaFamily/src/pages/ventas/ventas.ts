import { checkAuth } from "../../services/auth.service";
checkAuth();

import { getProductos } from "../../services/productos.service";
import { getVentas, createVenta, getDetalleVenta, anularVenta } from "../../services/ventas.service";
import { getCategorias } from "../../services/categorias.service";
import { getPromociones } from "../../services/promociones.service";
import type { Categoria } from "../../models/Categoria";
import type { Producto } from "../../models/Producto";
import type { Promocion } from "../../models/Promocion";
import type { DetalleVentaRequest } from "../../models/DetalleVentaRequest";
import type { Venta } from "../../models/Venta";
import { Html5Qrcode } from "html5-qrcode";
import { confirmar } from "../../services/confirm.service";
import { formatearCantidad, redondearCantidad } from "../../utils/format";

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

const modalVentasRegistradas = document.getElementById("modal-ventas-registradas") as HTMLDivElement;
const btnVerVentasRegistradas = document.getElementById("btn-ver-ventas-registradas") as HTMLButtonElement;
const btnCerrarVentasRegistradas = document.getElementById("btn-cerrar-ventas-registradas") as HTMLButtonElement;

const btnScanCamara = document.getElementById("btn-scan-camara") as HTMLButtonElement;
const modalScan = document.getElementById("modal-scan") as HTMLDivElement;
const btnCerrarScan = document.getElementById("btn-cerrar-scan") as HTMLButtonElement;

const productosSection = document.getElementById("productos-section") as HTMLDivElement;
const productosGrid = document.getElementById("productos-grid") as HTMLDivElement;
const filtrosCategorias = document.getElementById("filtros-categorias") as HTMLDivElement;

const toast = document.getElementById("toast") as HTMLDivElement;
const selectCliente = document.getElementById("select-cliente") as HTMLSelectElement;
/* =======================
   ESTADO
======================= */
let productos: Producto[] = [];
let categorias: Categoria[] = [];
let promosPorProducto: Map<number, Promocion> = new Map();
let carrito: DetalleVentaRequest[] = [];
let html5QrCode: Html5Qrcode;
let escaneando = false;
let ultimoCodigoEscaneado: string | null = null;
let productoSeleccionado: Producto | null = null;
let categoriaActivaId: number | null = null;

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

  if (esProductoPesable(producto)) {
    // A propósito SIN precargar: si dejáramos fraccionMinima (~0.001kg) acá,
    // un Enter en el buscador terminaba agregando "0.001 kg" antes de que el
    // vendedor pudiera tipear el peso real. Queda vacío + placeholder claro
    // para que sea evidente qué hay que escribir.
    inputCantidad.placeholder = `Ingresá el peso en ${unidadProducto(producto)}`;
    inputCantidad.value = "";
  } else {
    inputCantidad.placeholder = "Cantidad en unidades";
    inputCantidad.value = step.toString();
  }
}

function formatearCantidadConUnidad(cantidad: number, producto?: Producto) {
  if (!producto) return formatearCantidad(cantidad);
  return `${formatearCantidad(cantidad)} ${unidadProducto(producto)}`;
}

function desglosePaquete(cantidad: number, producto?: Producto): { paquetes: number; sueltas: number } | null {
  const upp = producto?.unidadesPorPaquete;
  if (!producto || !upp || upp < 2 || esProductoPesable(producto)) return null;
  return { paquetes: Math.floor(cantidad / upp), sueltas: cantidad % upp };
}

function formatearCantidadConPaquete(cantidad: number, producto?: Producto): string {
  const base = formatearCantidadConUnidad(cantidad, producto);
  const desglose = desglosePaquete(cantidad, producto);
  if (!desglose) return base;
  return `${base} (${desglose.paquetes} paq + ${desglose.sueltas} u)`;
}

/* =======================
   CARGA DE DATOS
======================= */
async function cargarProductos() {
  const [productosData, categoriasData, promociones] = await Promise.all([
    getProductos(),
    getCategorias(),
    getPromociones().catch(() => []) // no bloquea Ventas si falla (ej. red)
  ]);
  productos = productosData;
  categorias = categoriasData;
  productos.sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  categorias.sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
  promosPorProducto = new Map(
    promociones.filter(p => p.activo).map(p => [p.productoId, p])
  );
  renderFiltrosCategorias();
  renderProductosGrid();
}

// Mismo cálculo que el backend (VentaService.calcularSubtotal) — solo para
// el preview del carrito antes de confirmar. La fuente de verdad real es
// siempre la respuesta del backend al registrar la venta.
function subtotalConPromo(producto: Producto, cantidad: number): number {
  const promo = promosPorProducto.get(producto.id!);
  if (promo && producto.tipoCantidad === "UNIDAD") {
    const grupos = Math.floor(cantidad / promo.cantidadLleva);
    const resto = cantidad % promo.cantidadLleva;
    const unidadesAPagar = grupos * promo.cantidadPaga + resto;
    return unidadesAPagar * producto.precioVenta;
  }
  return producto.precioVenta * cantidad;
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
      if (await confirmar(`¿Seguro que desea anular la venta #${v.id}?`)) {
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

  const promo = promosPorProducto.get(prod.id!);

  card.innerHTML = `
    <div class="producto-imagen">
      ${prod.imagenUrl ? `<img src="${prod.imagenUrl}" alt="${prod.nombre}">` : ""}
      ${promo ? `<span class="promo-badge">${promo.cantidadLleva}x${promo.cantidadPaga}</span>` : ""}
    </div>
    <div class="producto-info">
      <div class="producto-nombre">${prod.nombre}</div>
      <div class="producto-precio">$${prod.precioVenta.toFixed(2)} / ${unidadProducto(prod)}</div>
      <div class="producto-stock">Stock: ${formatearCantidadConPaquete(prod.stock, prod)}</div>
    </div>
  `;
  card.addEventListener("click", () => {
    if (esProductoPesable(prod)) {
      // Por peso: hace falta que el vendedor tipee la cantidad exacta, un
      // click no alcanza. Mismo comportamiento que antes.
      seleccionarProducto(prod);
      return;
    }

    // Por unidad: click = +1 directo al carrito (patrón "tap to add" de
    // POS), sin pasar por el buscador/Enter. Click repetido va sumando.
    agregarAlCarrito(prod.id!, 1);
    flashCard(card);
    const item = carrito.find(i => i.productoId === prod.id);
    mostrarToast(`${prod.nombre} agregado (x${item?.cantidad ?? 1})`);
  });
  return card;
}

function flashCard(card: HTMLDivElement) {
  card.classList.add("agregado-flash");
  setTimeout(() => card.classList.remove("agregado-flash"), 350);
}

function renderFiltrosCategorias() {
  filtrosCategorias.innerHTML = "";

  const btnTodos = document.createElement("button");
  btnTodos.className = "filtro-cat-btn" + (categoriaActivaId === null ? " activo" : "");
  btnTodos.textContent = "Todos";
  btnTodos.addEventListener("click", () => {
    categoriaActivaId = null;
    actualizarActivoCat();
    renderProductosGrid(inputBuscarProducto.value);
  });
  filtrosCategorias.appendChild(btnTodos);

  categorias.forEach(cat => {
    const btn = document.createElement("button");
    btn.className = "filtro-cat-btn" + (categoriaActivaId === cat.id ? " activo" : "");
    btn.dataset.catId = String(cat.id);
    btn.textContent = cat.nombre;
    btn.addEventListener("click", () => {
      categoriaActivaId = cat.id ?? null;
      actualizarActivoCat();
      renderProductosGrid(inputBuscarProducto.value);
    });
    filtrosCategorias.appendChild(btn);
  });
}

function actualizarActivoCat() {
  filtrosCategorias.querySelectorAll<HTMLButtonElement>(".filtro-cat-btn").forEach(btn => {
    const catId = btn.dataset.catId ? Number(btn.dataset.catId) : null;
    btn.classList.toggle("activo", catId === categoriaActivaId);
  });
}

function renderProductosGrid(filtro: string = "") {
  productosGrid.innerHTML = "";

  const textoFiltro = filtro.toLowerCase().trim();

  let productosFiltrados = textoFiltro
    ? productos.filter(p =>
        p.nombre.toLowerCase().includes(textoFiltro) ||
        p.codigoBarras?.toLowerCase().includes(textoFiltro)
      )
    : [...productos];

  if (categoriaActivaId !== null) {
    productosFiltrados = productosFiltrados.filter(p => p.categoriaId === categoriaActivaId);
  }

  if (productosFiltrados.length === 0) {
    productosGrid.innerHTML = `<div class="sin-resultados">No se encontraron productos</div>`;
    return;
  }

  productosFiltrados.forEach(prod => productosGrid.appendChild(crearProductoCard(prod)));
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
    const subtotal = subtotalConPromo(prod, item.cantidad);
    total += subtotal;

    // El +/- solo aparece en productos por unidad: en los por peso, tocar
    // "+1" no tiene sentido (la fracción mínima es el gramo) y lo que hace
    // falta ahí es re-tipear un peso exacto, no incrementar de a poco.
    const stepper = esProductoPesable(prod)
      ? `<span class="carrito-cantidad-texto">${formatearCantidadConPaquete(item.cantidad, prod)}</span>`
      : `<div class="carrito-stepper">
          <button class="btn-stepper" data-index="${index}" data-delta="-1" aria-label="Quitar una unidad">−</button>
          <span class="carrito-cantidad-texto">${formatearCantidadConPaquete(item.cantidad, prod)}</span>
          <button class="btn-stepper" data-index="${index}" data-delta="1" aria-label="Agregar una unidad">+</button>
        </div>`;

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${prod.nombre}</td>
      <td>${stepper}</td>
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

  document.querySelectorAll(".btn-stepper").forEach(btn => {
    btn.addEventListener("click", () => {
      const index = Number((btn as HTMLElement).dataset.index);
      const delta = Number((btn as HTMLElement).dataset.delta);
      const item = carrito[index];
      const nueva = redondearCantidad(item.cantidad + delta);
      // Bajar a 0 con el "−" saca la línea entera: es lo que un vendedor
      // espera que pase, no dejar un renglón en 0 dando vueltas.
      if (nueva <= 0) {
        carrito.splice(index, 1);
      } else {
        item.cantidad = nueva;
      }
      renderCarrito();
    });
  });
}

// Agrega cantidad al carrito, sumando a la línea existente si el producto
// ya estaba (reescanear/re-clickear el mismo producto no debe crear una
// fila duplicada). Usado tanto por btnAgregar como por el click directo en
// las cards de productos por unidad.
function agregarAlCarrito(productoId: number, cantidad: number) {
  const existente = carrito.find(item => item.productoId === productoId);
  if (existente) {
    existente.cantidad += cantidad;
  } else {
    carrito.push({ productoId, cantidad });
  }
  renderCarrito();
}

// Descuenta localmente lo vendido, sin volver a pedir todo el listado
// al backend: la venta ya restó el stock ahí, acá solo reflejamos ese
// mismo descuento en el array que tenemos en memoria.
function actualizarStockLocal(itemsVendidos: DetalleVentaRequest[]) {
  itemsVendidos.forEach(item => {
    const prod = productos.find(p => p.id === item.productoId);
    if (prod) prod.stock = redondearCantidad(prod.stock - item.cantidad);
  });
  renderProductosGrid(inputBuscarProducto.value);
}

/* =======================
   FUNCIONES CLAVE
======================= */

// Atajo tipo caja de supermercado: "3x<código>" agrega 3 unidades en vez de
// 1, y "1.5x<código>" permite pedir un peso fraccionario para productos por
// peso (antes solo aceptaba enteros). Los códigos de barra reales son
// puramente numéricos, así que esto nunca choca con un escaneo normal (de
// teclado o cámara).
function extraerMultiplicador(codigo: string): { multiplicador: number | null; codigoBarras: string } {
  const match = codigo.match(/^(\d+(?:\.\d+)?)x(.+)$/i);
  if (!match) return { multiplicador: null, codigoBarras: codigo };
  return { multiplicador: Number(match[1]), codigoBarras: match[2].trim() };
}

// Devuelve el producto encontrado (y el multiplicador, si se usó el atajo
// Nx<código>) o null si no existe. Antes esta función mostraba el toast de
// "no encontrado" Y el caller (el handler de Enter) mostraba OTRO toast
// igual al ver que no había producto seleccionado — quedaba duplicado. Ahora
// hay un solo punto que decide y avisa: acá.
async function procesarCodigoEscaneado(codigo: string): Promise<{ producto: Producto; multiplicador: number | null } | null> {
  codigo = codigo.trim();

  if (!codigo) return null;

  if (!escaneando) escaneando = true;

  if (!productos.length) await cargarProductos();

  const { multiplicador, codigoBarras } = extraerMultiplicador(codigo);
  const producto = productos.find(p => String(p.codigoBarras) === codigoBarras);

  if (!producto) {
    mostrarToast("Producto no encontrado", true);
    // Selecciona el texto para que el próximo escaneo/tipeo lo sobrescriba
    // directo, sin tener que borrarlo a mano.
    inputBuscarProducto.select();
    return null;
  }

  seleccionarProducto(producto);

  if (multiplicador && multiplicador > 0) {
    inputCantidad.value = multiplicador.toString();
    mostrarToast(`Producto encontrado: ${producto.nombre} (x${multiplicador})`);
  }

  await cerrarScanner();
  return { producto, multiplicador };
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
    tr.innerHTML = `<td>${nombre}</td><td>${formatearCantidadConPaquete(item.cantidad, producto)}</td><td>$${item.precioUnitario.toFixed(2)} / ${producto ? unidadProducto(producto) : "u"}</td><td>$${item.subtotal.toFixed(2)}</td>`;
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
  if (!productoId || cantidad <= 0) { mostrarToast("Seleccione un producto válido y cantidad", true); return; }

  const producto = productos.find(p => p.id === productoId);
  if (!producto) return;
  if (!esProductoPesable(producto) && !Number.isInteger(cantidad)) {
    mostrarToast("Este producto se vende por unidad, la cantidad debe ser entera", true);
    return;
  }

  agregarAlCarrito(productoId, cantidad);

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
  if (!carrito.length) { mostrarToast("No hay productos en la venta", true); return; }

  let ventaCreada;
  try {
    ventaCreada = await createVenta({ detalles: carrito });
  } catch (error) {
    // Antes esto quedaba sin manejar: la venta fallaba en silencio, sin
    // toast ni error visible, y el carrito quedaba "colgado". Ahora se
    // avisa y el carrito se conserva intacto para poder reintentar.
    mostrarToast((error as Error).message || "Error al registrar venta", true);
    return;
  }

  actualizarStockLocal(carrito);
  carrito = [];
  renderCarrito();
  cargarVentas();

  await imprimirTicket(ventaCreada);

  mostrarToast("Venta registrada exitosamente");
  inputBuscarProducto.focus();
});

btnCerrarDetalle.addEventListener("click", () => modalDetalle.classList.add("hidden"));

btnVerVentasRegistradas.addEventListener("click", () => modalVentasRegistradas.classList.remove("hidden"));
btnCerrarVentasRegistradas.addEventListener("click", () => modalVentasRegistradas.classList.add("hidden"));

inputBuscarProducto.addEventListener("keydown", async (e) => {
  if (e.key !== "Enter") return;
  e.preventDefault();

  let producto: Producto | null = null;
  let multiplicador: number | null = null;

  if (!inputBuscarProducto.dataset.id) {
    const resultado = await procesarCodigoEscaneado(inputBuscarProducto.value.trim());
    if (!resultado) return; // procesarCodigoEscaneado ya mostró el toast de "no encontrado"
    producto = resultado.producto;
    multiplicador = resultado.multiplicador;
  } else {
    producto = productos.find(p => p.id === Number(inputBuscarProducto.dataset.id)) ?? null;
  }

  if (!producto) return;

  // Por peso, sin cantidad explícita vía Nx<código>: no agregar solo con la
  // fracción mínima. Dejamos el input de cantidad enfocado (ya vacío, ver
  // configurarInputCantidad) para que el vendedor tipee el peso real y lo
  // confirme con un segundo Enter ahí.
  if (esProductoPesable(producto) && !multiplicador) {
    inputCantidad.focus();
    return;
  }

  btnAgregar.click();
});

// Enter en "Cantidad" agrega al carrito, igual que tocar "Agregar"
// (útil cuando el producto se eligió a mano tocando una tarjeta).
inputCantidad.addEventListener("keydown", (e) => {
  if (e.key !== "Enter") return;
  e.preventDefault();
  btnAgregar.click();
});

// Atajo para confirmar la venta sin mouse. Ctrl+Enter a propósito (no un
// Enter solo): un Enter suelto es fácil de disparar sin querer navegando el
// resto de la pantalla con teclado, y confirmar una venta por accidente es
// mucho peor que agregar un producto de más.
document.addEventListener("keydown", (e) => {
  if (!(e.ctrlKey && e.key === "Enter")) return;
  if (!carrito.length) return;
  if (!modalDetalle.classList.contains("hidden")) return;
  if (modalVentasRegistradas && !modalVentasRegistradas.classList.contains("hidden")) return;
  // Hay un producto (típicamente por peso) seleccionado pero todavía no
  // agregado al carrito: forzar a terminar de cargarlo antes de confirmar.
  if (productoSeleccionado && inputBuscarProducto.dataset.id) return;

  e.preventDefault();
  btnConfirmar.click();
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
        <td style="text-align:center">${formatearCantidadConPaquete(d.cantidad, producto)}</td>
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

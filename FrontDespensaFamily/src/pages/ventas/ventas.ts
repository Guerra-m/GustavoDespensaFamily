import { habilitarCierreConEscape } from "../../utils/modales";
import { checkAuth, getUsername, isAdmin, puedeVerCaja } from "../../services/auth.service";
checkAuth();

import { getProductos } from "../../services/productos.service";
import { getVentasPaginado, createVenta, cotizarVenta, getDetalleVenta, anularVenta } from "../../services/ventas.service";
import { getCategorias } from "../../services/categorias.service";
import { getPromociones } from "../../services/promociones.service";
import { getDescuentos } from "../../services/descuentos.service";
import type { Descuento } from "../../models/Descuento";
import type { Categoria } from "../../models/Categoria";
import type { Producto } from "../../models/Producto";
import type { Promocion } from "../../models/Promocion";
import type { DetalleVentaRequest } from "../../models/DetalleVentaRequest";
import type { MetodoPago } from "../../models/MetodoPago";
import type { Cotizacion } from "../../models/Cotizacion";
import { Html5Qrcode } from "html5-qrcode";
import { confirmar } from "../../services/confirm.service";
import { formatearCantidad, redondearCantidad, resolverImagenUrl, esc, formatearFechaHora, formatearMoneda } from "../../utils/format";
import { filtrarProductos } from "../../utils/filtroProductos";
import { crearNavegacionTeclado } from "./ventas.atajos";
import { crearImpresorTicket, etiquetaMetodoPago } from "./ventas.ticket";
import { esProductoPesable, unidadProducto, fraccionMinima, formatearCantidadConPaquete, formatearCantidadDeProducto, desglosePaquete } from "../../utils/producto";
import { mostrarToast } from "../../utils/toast";
import { getConfiguracion } from "../../services/configuracion.service";
import { crearSesionDeEscaneo, suscribirseAEscaneo } from "../../services/escaneo.service";
import QRCode from "qrcode";

/* =======================
   ELEMENTOS DEL DOM
======================= */
const inputBuscarProducto = document.getElementById("input-buscar-producto") as HTMLInputElement;
const inputCantidad = document.getElementById("input-cantidad") as HTMLInputElement;
const btnAgregar = document.getElementById("btn-agregar") as HTMLButtonElement;
const btnConfirmar = document.getElementById("btn-confirmar") as HTMLButtonElement;
const inputCliente = document.getElementById("input-cliente") as HTMLInputElement;

// ---------------- Método de pago ----------------
// No se resetea a EFECTIVO después de cada venta a propósito: en una racha
// de ventas con tarjeta (por ejemplo, si el POS de efectivo está en el otro
// mostrador) obligar a re-tocar el botón en cada venta molesta más de lo que
// evita errores.
let metodoPagoSeleccionado: MetodoPago = "EFECTIVO";
const botonesMetodoPago = document.querySelectorAll<HTMLButtonElement>(".metodo-pago-btn");
botonesMetodoPago.forEach(btn => {
  btn.addEventListener("click", () => {
    metodoPagoSeleccionado = btn.dataset.metodo as MetodoPago;
    botonesMetodoPago.forEach(b => b.classList.toggle("activo", b === btn));
  });
});

const tablaDetalle = document.getElementById("tabla-detalle") as HTMLDivElement;
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

const btnEscanearCelular = document.getElementById("btn-escanear-celular") as HTMLButtonElement;
const modalScanCelular = document.getElementById("modal-scan-celular") as HTMLDivElement;
const btnCerrarScanCelular = document.getElementById("btn-cerrar-scan-celular") as HTMLButtonElement;
const scanCelularQr = document.getElementById("scan-celular-qr") as HTMLCanvasElement;
const scanCelularEstado = document.getElementById("scan-celular-estado") as HTMLParagraphElement;

const productosSection = document.getElementById("productos-section") as HTMLDivElement;
const productosGrid = document.getElementById("productos-grid") as HTMLDivElement;
const selectCategoria = document.getElementById("select-categoria") as HTMLSelectElement;

/* =======================
   ESTADO
======================= */
let productos: Producto[] = [];
let umbralStockBajo = 10; // valor por defecto hasta que cargue la configuración real
let categorias: Categoria[] = [];
let promosPorProducto: Map<number, Promocion> = new Map();
// DF-UX-005: descuentos por % activos, indexados por producto y por
// categoria por separado -- descuentoEfectivoPara resuelve la precedencia
// (producto puntual gana sobre el de su categoria), MISMA regla que
// DescuentoService.porcentajeEfectivoPara en el backend. No se duplica el
// cálculo del precio acá, solo si corresponde mostrar el badge.
let descuentosPorProducto: Map<number, number> = new Map();
let descuentosPorCategoria: Map<number, number> = new Map();
let carrito: DetalleVentaRequest[] = [];
let html5QrCode: Html5Qrcode;
let escaneando = false;
// Evita reprocesar dos veces la misma lectura del escáner por cámara.
let ultimoCodigoEscaneado: string | null = null;
void ultimoCodigoEscaneado;
let productoSeleccionado: Producto | null = null;
let categoriaActivaId: number | null = null;

// ---------------- DF-BUG-001: cotización del carrito ----------------
// Única fuente de precios del carrito: lo que devuelve POST /ventas/cotizar
// (VentaService.cotizar en el backend), que comparte el cálculo con la venta
// real. Antes esto se recalculaba acá mismo (subtotalConPromo), aplicando
// solo promos —nunca descuentos— y sin redondear cantidades pesables: el
// carrito llegó a mostrar $2.860 para una línea que el ticket y la base
// cobraban $286.
let cotizacionActual: Cotizacion | null = null;
let cotizando = false;
let secuenciaCotizacion = 0;
let handleDebounceCotizacion: ReturnType<typeof setTimeout> | null = null;

// ---------------- DF-BUG-003: idempotencia de la confirmación ----------------
let ventaEnCurso = false;
let claveIdempotenciaActual: string | null = null;

function generarClaveIdempotencia(): string {
  // crypto.randomUUID existe en todos los navegadores/Electron que usa la
  // app; sin fallback a propósito, si faltara es mejor que rompa fuerte acá
  // y no que se generen claves débiles.
  return crypto.randomUUID();
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

/* =======================
   CARGA DE DATOS
======================= */
function indexarDescuentos(descuentos: Descuento[]) {
  descuentosPorProducto = new Map();
  descuentosPorCategoria = new Map();
  descuentos.filter(d => d.activo).forEach(d => {
    if (d.alcance === "PRODUCTO" && d.productoId != null) {
      descuentosPorProducto.set(d.productoId, d.porcentaje);
    } else if (d.alcance === "CATEGORIA" && d.categoriaId != null) {
      descuentosPorCategoria.set(d.categoriaId, d.porcentaje);
    }
  });
}

/** 0 si no hay descuento activo -- mismo criterio que DescuentoService.porcentajeEfectivoPara. */
function descuentoEfectivoPara(producto: Producto): number {
  const directo = descuentosPorProducto.get(producto.id!);
  if (directo != null) return directo;
  if (producto.categoriaId == null) return 0;
  return descuentosPorCategoria.get(producto.categoriaId) ?? 0;
}

// DF-UX-003: antes esto no tenía try/catch -- un error de red al abrir
// Ventas (por ejemplo, el backend recién reiniciando) dejaba la grilla vacía
// sin ningún aviso, y quien vende no tenía forma de saber si tenía que
// esperar o recargar.
async function cargarProductos() {
  try {
    const [productosData, categoriasData, promociones, descuentos] = await Promise.all([
      getProductos(),
      getCategorias(),
      getPromociones().catch(() => []), // no bloquea Ventas si falla (ej. red)
      getDescuentos().catch(() => [])
    ]);
    productos = productosData;
    categorias = categoriasData;
    productos.sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
    categorias.sort((a, b) => a.nombre.localeCompare(b.nombre, "es"));
    promosPorProducto = new Map(
      promociones.filter(p => p.activo).map(p => [p.productoId, p])
    );
    indexarDescuentos(descuentos);
    renderFiltrosCategorias();
    renderProductosGrid();
  } catch (error) {
    mostrarToast(
      `No se pudo cargar el catálogo: ${(error as Error).message || "error de red"}`,
      true
    );
  }
}

// DF-PERF-001: /ventas sin paginar mandaba 562 KB con 3.055 ventas
// (~13 MB/año). El historial pagina en el servidor -- TAMANIO_PAGINA_VENTAS
// filas por vez, con Anterior/Siguiente. paginaVentasActual es 0-based,
// igual que el parametro `page` del backend.
const TAMANIO_PAGINA_VENTAS = 50;
let paginaVentasActual = 0;
let totalPaginasVentas = 1;

const btnPaginaAnterior = document.getElementById("btn-pagina-anterior") as HTMLButtonElement;
const btnPaginaSiguiente = document.getElementById("btn-pagina-siguiente") as HTMLButtonElement;
const textoPaginaVentas = document.getElementById("texto-pagina-ventas") as HTMLSpanElement;

async function cargarVentas(page: number = paginaVentasActual) {
  const pagina = await getVentasPaginado(page, TAMANIO_PAGINA_VENTAS);
  paginaVentasActual = pagina.pagina;
  totalPaginasVentas = Math.max(pagina.totalPaginas, 1);

  tablaVentas.innerHTML = "";

  // Sin permiso de caja el servidor devuelve solo las ventas propias del día
  // (DF-SEC-003): el título lo dice para que no parezca que faltan ventas.
  const titulo = document.getElementById("titulo-ventas-registradas");
  if (titulo) titulo.textContent = puedeVerCaja() ? "Ventas Registradas" : "Tus ventas de hoy";

  textoPaginaVentas.textContent = `Página ${paginaVentasActual + 1} de ${totalPaginasVentas} (${pagina.totalElementos} ventas)`;
  btnPaginaAnterior.disabled = paginaVentasActual <= 0;
  btnPaginaSiguiente.disabled = paginaVentasActual >= totalPaginasVentas - 1;

  // El backend ya ordena mas nueva primero (ORDER BY fecha_hora DESC) --
  // antes esto hacia [...ventas].reverse() porque GET /ventas sin paginar
  // venia en orden de insercion ascendente.
  pagina.contenido.forEach(v => {
    if (!v.id) return;

    const anulada = v.estado === "ANULADA";

    const tr = document.createElement("tr");
    // Las anuladas ahora vienen en el listado y se muestran tachadas. Antes se
    // filtraban del lado del servidor y desaparecían del historial, que es
    // justo lo que hacía servible cobrar una venta y anularla.
    if (anulada) tr.classList.add("venta-anulada");
    tr.innerHTML = `
      <td>${v.id}</td>
      <td>${formatearFechaHora(v.fechaHora)}</td>
      <td>${formatearMoneda(v.total)}</td>
      <td>${v.metodoPago ? etiquetaMetodoPago(v.metodoPago) : "—"}</td>
      <td>${esc(v.usuario) || "—"}</td>
      <td>
        ${anulada
          ? `<span class="etiqueta-anulada" title="Anulada por ${esc(v.anuladaPor) || "—"}">Anulada</span>`
          : `<button class="btn-imprimir">Imprimir</button>
             ${isAdmin() ? `<button class="btn-anular">Anular</button>` : ""}`}
      </td>
    `;

    tr.addEventListener("click", () => abrirDetalleVenta(v.id!));

    const btnAnular = tr.querySelector(".btn-anular") as HTMLButtonElement | null;
    btnAnular?.addEventListener("click", async (e) => {
      e.stopPropagation();
      if (await confirmar(`¿Seguro que desea anular la venta #${v.id}?`)) {
        await anularVenta(v.id!);
        cargarVentas();
      }
    });

    // DF-BUG-003: en una venta ANULADA este botón no se dibuja (el <td> de
    // arriba muestra la etiqueta "Anulada" en su lugar), así que querySelector
    // da null acá. Antes esto hacía `as HTMLButtonElement` sin chequear y
    // llamaba `.addEventListener` directo -- un TypeError a mitad del forEach
    // que cortaba el render: ninguna venta después de la anulada llegaba a
    // pintarse. `?.` evita depender de que el botón exista.
    const btnImprimir = tr.querySelector(".btn-imprimir") as HTMLButtonElement | null;
    btnImprimir?.addEventListener("click", async (e) => {
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
  if (prod.stock < umbralStockBajo) card.classList.add("stock-bajo");
  if (productoSeleccionado?.id === prod.id) card.classList.add("seleccionado");

  const promo = promosPorProducto.get(prod.id!);
  // DF-UX-005: antes el % de descuento no se veía en ningún lado hasta
  // llegar al carrito (o ni ahí, si no se lo miraba con atención) -- ahora
  // la tarjeta lo anuncia igual que ya hacía con el combo de Promocion.
  const descuentoPct = descuentoEfectivoPara(prod);

  card.innerHTML = `
    <div class="producto-imagen">
      ${prod.imagenUrl ? `<img src="${resolverImagenUrl(prod.imagenUrl)}" alt="${esc(prod.nombre)}">` : ""}
      ${descuentoPct > 0
        ? `<span class="descuento-badge">-${formatearCantidad(descuentoPct)}%</span>`
        : promo ? `<span class="promo-badge">${promo.cantidadLleva}x${promo.cantidadPaga}</span>` : ""}
    </div>
    <div class="producto-info">
      <div class="producto-nombre">${esc(prod.nombre)}</div>
      <div class="producto-precio">${formatearMoneda(prod.precioVenta)} / ${unidadProducto(prod)}</div>
      <div class="producto-stock">Stock: ${formatearCantidadConPaquete(prod.stock, prod)}</div>
    </div>
  `;
  // DF-UX-007: `imagenUrl` puede apuntar a un archivo que ya no está (se
  // borró del disco, o es de otra instalación restaurada de un backup). El
  // <img> roto mostraba el ícono nativo del navegador MÁS el texto de `alt`
  // (el nombre del producto) desbordado sobre la tarjeta de 130px -- se leía
  // como texto superpuesto/roto. Sacar el <img> en el error dispara el
  // placeholder que ya existe en CSS (`.producto-imagen:not(:has(img))`),
  // el mismo cajón gris que se ve cuando el producto nunca tuvo imagen.
  const img = card.querySelector<HTMLImageElement>(".producto-imagen img");
  img?.addEventListener("error", () => img.remove(), { once: true });

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
    mostrarToast(`${esc(prod.nombre)} agregado (x${item?.cantidad ?? 1})`);
  });
  return card;
}

function flashCard(card: HTMLDivElement) {
  card.classList.add("agregado-flash");
  setTimeout(() => card.classList.remove("agregado-flash"), 350);
}

function renderFiltrosCategorias() {
  // El valor "" representa "Todos" (la opción ya está en el HTML); las
  // categorías se agregan después, ordenadas como llegaron de categorias().
  selectCategoria.querySelectorAll("option[data-cat-id]").forEach(o => o.remove());

  categorias.forEach(cat => {
    const option = document.createElement("option");
    option.value = String(cat.id);
    option.dataset.catId = String(cat.id);
    option.textContent = cat.nombre;
    selectCategoria.appendChild(option);
  });

  selectCategoria.value = categoriaActivaId === null ? "" : String(categoriaActivaId);
}

selectCategoria.addEventListener("change", () => {
  categoriaActivaId = selectCategoria.value === "" ? null : Number(selectCategoria.value);
  renderProductosGrid(inputBuscarProducto.value);
});

// DF-PERF-001: con el catálogo completo (2.024 productos en la auditoría)
// la grilla llegaba a 39.589 nodos DOM y 146ms por tecla. El escáner y el
// Enter con código de barras (procesarCodigoEscaneado) buscan sobre el
// array `productos` completo, NO sobre lo que está en pantalla, así que
// bajar esto no les saca alcance: siguen encontrando cualquier producto,
// esté o no entre estas tarjetas.
const MAX_TARJETAS_POS = 60;

function renderProductosGrid(filtro: string = "") {
  productosGrid.innerHTML = "";
  // El catálogo cambió (filtro o categoría): la marca de navegación por
  // flechas de antes ya no corresponde a nada visible.
  navegacionTeclado.resetNavegacion();

  const productosFiltrados = filtrarProductos(productos, { texto: filtro, categoriaId: categoriaActivaId });

  if (productosFiltrados.length === 0) {
    productosGrid.innerHTML = `<div class="sin-resultados">No se encontraron productos</div>`;
    return;
  }

  const truncado = productosFiltrados.length > MAX_TARJETAS_POS;
  const aRenderizar = truncado ? productosFiltrados.slice(0, MAX_TARJETAS_POS) : productosFiltrados;

  aRenderizar.forEach(prod => productosGrid.appendChild(crearProductoCard(prod)));

  if (truncado) {
    const aviso = document.createElement("div");
    aviso.className = "aviso-mas-productos";
    aviso.textContent =
      `Mostrando ${MAX_TARJETAS_POS} de ${productosFiltrados.length} — escribí más para encontrar el resto, o escaneá su código de barras`;
    productosGrid.appendChild(aviso);
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

  mostrarToast(`Producto seleccionado: ${esc(producto.nombre)}`);
}

/* =======================
   CARRITO
======================= */

// La línea cotizada para este item, si ya llegó del backend. Se busca por
// productoId nomás (no por cantidad): mientras la cotización nueva está en
// vuelo (debounce/red), mostrar la última que llegó para ese producto es
// mejor que un guion — y en cuanto se ajuste la cantidad, la próxima
// respuesta la reemplaza.
function lineaCotizadaPara(item: DetalleVentaRequest) {
  return cotizacionActual?.lineas.find(l => l.productoId === item.productoId);
}

function indicadorDescuento(linea: ReturnType<typeof lineaCotizadaPara>): string {
  if (!linea) return "";
  if (linea.descuentoPorcentaje) return `<span class="carrito-descuento">-${formatearCantidad(linea.descuentoPorcentaje)}%</span>`;
  if (linea.promoDescripcion) return `<span class="carrito-descuento">${esc(linea.promoDescripcion)}</span>`;
  return "";
}

// Solo dibuja lo que ya se sabe (carrito + última cotización disponible).
// No dispara ningún pedido de red — eso es tarea de programarCotizacion.
//
// DF-UX-001: antes esto armaba una <tr> de 5 columnas (Producto/Cantidad/
// Precio/Subtotal/Acción) que necesitaba ~524px de ancho mínimo para no
// recortar nada — en una notebook la columna del carrito nunca tiene eso.
// Ahora cada ítem es una tarjeta de dos renglones (nombre + cantidad arriba,
// subtotal + quitar abajo) que se envuelve/achica con el ancho disponible
// sin perder ningún dato. Sigue mostrando el subtotal cotizado por el
// backend (DF-BUG-001) y el indicador de descuento/stock, nada de eso se
// tocó.
function renderCarrito() {
  tablaDetalle.innerHTML = "";

  carrito.forEach((item, index) => {
    const prod = productos.find(p => p.id === item.productoId)!;
    const linea = lineaCotizadaPara(item);
    // Sin cotización todavía para esta línea (recién agregada, primera
    // vuelta): "…" en vez de inventar un precio del lado del cliente — eso
    // es exactamente lo que causaba DF-BUG-001.
    const subtotalTexto = linea ? formatearMoneda(linea.subtotal) : "…";
    const avisoStock = linea?.stockInsuficiente ? `<div class="carrito-sin-stock">Sin stock suficiente</div>` : "";

    // DF-BUG-002: mostrar acá lo mismo que ya cobró /ventas/cotizar. Antes se
    // formateaba item.cantidad (el valor crudo tipeado, redondeado del lado
    // del cliente) y con ciertos decimales eso no coincidía con la cantidad
    // que el backend redondeó con BigDecimal HALF_UP para calcular el
    // subtotal (ej: tipear 1,2345 kg mostraba "1,234" cuando el subtotal ya
    // cobraba 1,235). Con cotización en mano, linea.cantidad ES la cantidad
    // facturada -- se muestra esa. Sin cotización todavía (primera vuelta,
    // sin red aún), formatearCantidadConPaquete ya redondea bien (ver
    // DF-BUG-002 en utils/format.ts).
    const cantidadMostrada = linea?.cantidad ?? item.cantidad;

    // DF-UX-008: antes la cantidad entre los +/- era formatearCantidadConPaquete
    // (ej. "2 u (0 paq + 2 u)"), pensada para el texto suelto de antes. En la
    // fila de grid nueva ese texto tan largo no entraba en la columna angosta
    // de "cantidad" y empujaba el subtotal y la ✕ a un segundo renglón (el
    // caso concreto: "papel higienico", que se vende en paquetes de 5).
    // Ahora el stepper muestra solo la cantidad corta ("2 u") y el desglose
    // de paquetes baja a una línea chica debajo del nombre (ver
    // .carrito-item-paquete más abajo) -- ninguna columna del grid depende
    // de un texto de largo variable.
    const cantidadCorta = formatearCantidadDeProducto(cantidadMostrada, prod);
    const desglose = desglosePaquete(cantidadMostrada, prod);
    // Sin paquetes completos (0 paq + N u) el desglose no dice nada que la
    // cantidad corta no diga ya -- se omite en vez de mostrar "0 paq + 2 u".
    const desglosePaqueteHtml = desglose && desglose.paquetes > 0
      ? `<div class="carrito-item-paquete">${desglose.paquetes} paq + ${desglose.sueltas} u</div>`
      : "";

    // El +/- solo aparece en productos por unidad: en los por peso, tocar
    // "+1" no tiene sentido (la fracción mínima es el gramo) y lo que hace
    // falta ahí es re-tipear un peso exacto, no incrementar de a poco.
    const stepper = esProductoPesable(prod)
      ? `<span class="carrito-cantidad-texto">${cantidadCorta}</span>`
      : `<div class="carrito-stepper">
          <button class="btn-stepper" data-index="${index}" data-delta="-1" aria-label="Quitar una unidad">−</button>
          <span class="carrito-cantidad-texto">${cantidadCorta}</span>
          <button class="btn-stepper" data-index="${index}" data-delta="1" aria-label="Agregar una unidad">+</button>
        </div>`;

    // DF-UX-006/008 ("arreglar el carrito"): antes eran dos renglones por
    // ítem (nombre+cantidad arriba, subtotal+"Quitar" abajo) -- con los
    // botones de 32px+ que pide la auditoría (stepper, quitar) apilados en
    // dos renglones, cada ítem terminaba pesando ~80px de alto aunque el
    // contenido cupiera en uno solo. Un renglón en CSS grid con columnas
    // fijas (nombre 1fr | cantidad auto | subtotal auto | ✕ auto, ver
    // ventas.css) mide lo que mide el más alto de sus botones (~40-44px) en
    // vez de la suma de los dos, y ninguna columna se corre de línea aunque
    // el contenido de otra sea más largo (a diferencia del flex-wrap
    // anterior, que rompía la fila con nombres de producto por paquete).
    const fila = document.createElement("div");
    fila.className = "carrito-item";
    fila.innerHTML = `
      <div class="carrito-item-nombre-col">
        <span class="carrito-item-nombre">${esc(prod.nombre)} ${indicadorDescuento(linea)}</span>
        ${desglosePaqueteHtml}
      </div>
      ${stepper}
      <span class="carrito-item-subtotal">${subtotalTexto}</span>
      <button class="btn-quitar" data-index="${index}" aria-label="Quitar ${esc(prod.nombre)} del carrito" title="Quitar">✕</button>
      ${avisoStock}
    `;
    tablaDetalle.appendChild(fila);
  });

  actualizarTotal();

  document.querySelectorAll(".btn-quitar").forEach(btn => {
    btn.addEventListener("click", () => {
      const index = Number((btn as HTMLElement).dataset.index);
      carrito.splice(index, 1);
      onCarritoCambio();
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
      onCarritoCambio();
    });
  });
}

function actualizarTotal() {
  if (!carrito.length) {
    spanTotal.textContent = formatearMoneda(0);
    return;
  }
  if (cotizando && !cotizacionActual) {
    // Primera cotización de este carrito, todavía sin ninguna respuesta
    // previa que mostrar mientras tanto.
    spanTotal.textContent = "Calculando…";
    return;
  }
  if (cotizacionActual) {
    spanTotal.textContent = cotizando
      ? `${formatearMoneda(cotizacionActual.total)} (actualizando…)`
      : formatearMoneda(cotizacionActual.total);
    return;
  }
  spanTotal.textContent = "—";
}

// DF-BUG-001: pide la cotización real al backend con un debounce de 250ms —
// tipear "3" y después "0" para 30 unidades no tiene que disparar dos
// pedidos, solo el último importa. `secuenciaCotizacion` descarta cualquier
// respuesta que no sea la del ÚLTIMO pedido programado (una respuesta vieja
// que llega tarde no puede pisar a una más nueva que ya llegó, o peor, a un
// carrito que mientras tanto se vació).
function programarCotizacion() {
  if (handleDebounceCotizacion) clearTimeout(handleDebounceCotizacion);

  if (!carrito.length) {
    cotizacionActual = null;
    cotizando = false;
    secuenciaCotizacion++; // invalida cualquier pedido en vuelo
    actualizarTotal();
    return;
  }

  cotizando = true;
  actualizarTotal();

  const miSecuencia = ++secuenciaCotizacion;
  const detallesPedidos = carrito.map(item => ({ ...item }));

  handleDebounceCotizacion = setTimeout(async () => {
    try {
      const cotizacion = await cotizarVenta(detallesPedidos);
      if (miSecuencia !== secuenciaCotizacion) return; // ya no es la vigente

      cotizacionActual = cotizacion;
      cotizando = false;
      renderCarrito();
    } catch (error) {
      if (miSecuencia !== secuenciaCotizacion) return;

      cotizando = false;
      // Se conserva la última cotización buena (si había): un error de red
      // momentáneo no tiene que borrar el total que ya se sabía correcto.
      mostrarToast(
        `No se pudo actualizar el total: ${(error as Error).message || "error de red"}`,
        true
      );
      actualizarTotal();
    }
  }, 250);
}

/* =======================
   DF-UX-007: carrito persistente
   El carrito se perdía con un F5, con la sesión vencida (el interceptor de
   401 redirige al login) o con el reinicio por actualización, porque vivía
   solo en la variable `carrito` en memoria. Ahora se espeja en
   sessionStorage en cada cambio y se restaura al abrir la pantalla.
======================= */

// Atado al usuario logueado (no a la pestaña sola): si otra persona entra en
// la misma PC/pestaña con su usuario, la clave cambia y no hereda el
// carrito de quien vendía antes. sessionStorage ya de por sí no cruza
// pestañas ni sobrevive a cerrar el navegador, que es lo que corresponde
// para algo que se está por cobrar.
function claveStorageCarrito(): string {
  return `df-carrito-${getUsername() ?? "sin-usuario"}`;
}

function guardarCarritoEnStorage() {
  try {
    if (carrito.length) {
      sessionStorage.setItem(claveStorageCarrito(), JSON.stringify(carrito));
    } else {
      sessionStorage.removeItem(claveStorageCarrito());
    }
  } catch {
    // sessionStorage puede fallar (modo privado, cuota) — perder la
    // persistencia no tiene que tirar abajo la venta que se está armando.
  }
}

function limpiarCarritoStorage() {
  try { sessionStorage.removeItem(claveStorageCarrito()); } catch { /* ver guardarCarritoEnStorage */ }
}

/**
 * Lee el carrito guardado, si hay uno y tiene forma de tal. No valida stock
 * ni existencia de los productos acá — eso lo hace `restaurarCarritoSiHayGuardado`
 * una vez que `productos` ya está cargado, y es la MISMA cotización real
 * (POST /ventas/cotizar) la que termina de confirmar precio y stock.
 */
function leerCarritoDeStorage(): DetalleVentaRequest[] {
  try {
    const crudo = sessionStorage.getItem(claveStorageCarrito());
    if (!crudo) return [];
    const parseado = JSON.parse(crudo);
    if (!Array.isArray(parseado)) return [];
    return parseado.filter(
      (item): item is DetalleVentaRequest =>
        item && typeof item.productoId === "number" && typeof item.cantidad === "number" && item.cantidad > 0
    );
  } catch {
    return [];
  }
}

let carritoYaRestaurado = false;

/**
 * Se llama una sola vez, después de que `productos` está cargado. Descarta
 * las líneas de productos que ya no existen (de baja, o el id ya no es
 * válido) — el resto se re-cotiza normal contra el backend vía
 * `onCarritoCambio` → `programarCotizacion`, así que un producto sin stock
 * suficiente sigue apareciendo en el carrito pero avisando "Sin stock
 * suficiente", igual que si se hubiera armado recién.
 */
function restaurarCarritoSiHayGuardado() {
  if (carritoYaRestaurado) return;
  carritoYaRestaurado = true;

  const guardado = leerCarritoDeStorage();
  if (!guardado.length) return;

  const idsValidos = new Set(productos.map(p => p.id));
  const validos = guardado.filter(item => idsValidos.has(item.productoId));
  const descartados = guardado.length - validos.length;

  if (!validos.length) {
    limpiarCarritoStorage();
    if (descartados) mostrarToast("El carrito guardado tenía productos que ya no existen; se descartó", true);
    return;
  }

  carrito = validos;
  onCarritoCambio();

  if (descartados) {
    mostrarToast(`Se restauró el carrito (${descartados} producto(s) ya no disponible(s) se quitaron)`, true);
  } else {
    mostrarToast("Se restauró el carrito de la venta en curso");
  }
}

// Todo lo que cambia el carrito pasa por acá: redibuja con lo que ya se sabe
// y programa la cotización real. También invalida la clave de idempotencia
// de la confirmación anterior — un carrito distinto es un pedido distinto.
function onCarritoCambio() {
  claveIdempotenciaActual = null;
  guardarCarritoEnStorage();
  renderCarrito();
  programarCotizacion();
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
  onCarritoCambio();
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
/**
 * Busca por código de barras (acepta la forma "3x<código>" del lector).
 *
 * `avisarSiNoEsta` en false lo deja fallar callado: lo usa el Enter del
 * buscador, que si no encuentra un código todavía tiene otra carta para jugar
 * —el producto que se está viendo en la grilla— y no quiere mostrar un
 * "no encontrado" que después se desmiente solo.
 */
async function procesarCodigoEscaneado(codigo: string, avisarSiNoEsta = true): Promise<{ producto: Producto; multiplicador: number | null } | null> {
  codigo = codigo.trim();

  if (!codigo) return null;

  if (!escaneando) escaneando = true;

  if (!productos.length) await cargarProductos();

  const { multiplicador, codigoBarras } = extraerMultiplicador(codigo);
  const producto = productos.find(p => String(p.codigoBarras) === codigoBarras);

  if (!producto) {
    if (avisarSiNoEsta) {
      mostrarToast("Producto no encontrado", true);
      // Selecciona el texto para que el próximo escaneo/tipeo lo sobrescriba
      // directo, sin tener que borrarlo a mano.
      inputBuscarProducto.select();
    }
    return null;
  }

  seleccionarProducto(producto);

  if (multiplicador && multiplicador > 0) {
    inputCantidad.value = multiplicador.toString();
    mostrarToast(`Producto encontrado: ${esc(producto.nombre)} (x${multiplicador})`);
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
    tr.innerHTML = `<td>${esc(nombre)}</td><td>${formatearCantidadConPaquete(item.cantidad, producto)}</td><td>${formatearMoneda(item.precioUnitario)} / ${producto ? unidadProducto(producto) : "u"}</td><td>${formatearMoneda(item.subtotal)}</td>`;
    tablaDetalleVenta.appendChild(tr);
    total += item.subtotal;
  });

  detalleTotal.textContent = formatearMoneda(total);
  modalDetalle.classList.remove("hidden");
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

// DF-BUG-003: un único camino para confirmar, usado tanto por el click del
// botón como por el atajo Ctrl+Enter (antes cada uno tenía su propio
// entrypoint y ninguno se cuidaba de una segunda llamada en simultáneo — un
// doble click real llegó a crear dos ventas del mismo carrito en el mismo
// milisegundo). `ventaEnCurso` corta cualquier llamada mientras la anterior
// sigue en vuelo, y el botón se deshabilita como refuerzo visual — mismo
// patrón que compras.ts y login.ts.
async function confirmarVenta() {
  if (ventaEnCurso) return;
  if (!carrito.length) { mostrarToast("No hay productos en la venta", true); return; }

  ventaEnCurso = true;
  btnConfirmar.disabled = true;
  const textoOriginalBtnConfirmar = btnConfirmar.textContent;
  btnConfirmar.textContent = "Confirmando...";

  // Un UUID por intento (crypto.randomUUID). Si esta misma clave ya viajó en
  // un intento anterior que falló por red, se reutiliza (no se regenera acá
  // arriba) para que el backend pueda reconocer el reintento como el MISMO
  // pedido — ver VentaController / V12__venta_clave_idempotencia.
  const clave = claveIdempotenciaActual ?? (claveIdempotenciaActual = generarClaveIdempotencia());
  const cotizacionDeEsteCarrito = cotizacionActual;

  try {
    const cliente = inputCliente.value.trim();
    const ventaCreada = await createVenta({
      detalles: carrito,
      metodoPago: metodoPagoSeleccionado,
      cliente: cliente || undefined
    }, clave);

    // Éxito: el próximo intento (si el vendedor arma otro carrito) es un
    // pedido nuevo, con su propia clave.
    claveIdempotenciaActual = null;

    actualizarStockLocal(carrito);
    carrito = [];
    limpiarCarritoStorage(); // DF-UX-007: la venta ya se confirmó, no hay nada que restaurar
    cotizacionActual = null;
    // A diferencia del método de pago, el cliente sí se limpia: el próximo
    // carrito casi seguro es para otra persona, y dejarlo puesto sería un
    // error mucho más probable que útil.
    inputCliente.value = "";
    renderCarrito();
    cargarVentas(0); // la venta que se acaba de confirmar es la más nueva -> página 0

    await imprimirTicket(ventaCreada, cotizacionDeEsteCarrito);

    mostrarToast("Venta registrada exitosamente");
    inputBuscarProducto.focus();
  } catch (error) {
    // Antes esto quedaba sin manejar: la venta fallaba en silencio, sin
    // toast ni error visible, y el carrito quedaba "colgado". Ahora se
    // avisa y el carrito se conserva intacto para poder reintentar — con la
    // MISMA clave de idempotencia, que no se tocó en este catch.
    mostrarToast((error as Error).message || "Error al registrar venta", true);
  } finally {
    ventaEnCurso = false;
    btnConfirmar.disabled = false;
    btnConfirmar.textContent = textoOriginalBtnConfirmar;
  }
}

btnConfirmar.addEventListener("click", confirmarVenta);

btnCerrarDetalle.addEventListener("click", () => modalDetalle.classList.add("hidden"));

btnVerVentasRegistradas.addEventListener("click", () => modalVentasRegistradas.classList.remove("hidden"));
btnCerrarVentasRegistradas.addEventListener("click", () => modalVentasRegistradas.classList.add("hidden"));

btnPaginaAnterior.addEventListener("click", () => {
  if (paginaVentasActual > 0) cargarVentas(paginaVentasActual - 1);
});
btnPaginaSiguiente.addEventListener("click", () => {
  if (paginaVentasActual < totalPaginasVentas - 1) cargarVentas(paginaVentasActual + 1);
});

inputBuscarProducto.addEventListener("keydown", async (e) => {
  if (e.key !== "Enter") return;
  e.preventDefault();

  // El Enter resuelve en orden, de lo más explícito a lo más inferido, y
  // nunca adivina entre varios.
  //
  // Antes solo entendía dos caminos: un producto ya elegido del desplegable, o
  // un código de barras exacto. Escribir "coca" y apretar Enter caía en el
  // segundo, no encontraba ningún código que se llamara "coca" y contestaba
  // "Producto no encontrado" — con el producto ahí a la vista en la grilla.
  // Eso es lo que Martín describió como "a veces el Enter carga y a veces no":
  // funcionaba con el lector y con el desplegable, no al tipear un nombre.
  const tarjetaNavegada = !inputBuscarProducto.dataset.id ? navegacionTeclado.tarjetaNavegadaActual() : undefined;
  if (tarjetaNavegada) {
    // Una tarjeta marcada con las flechas gana sobre todo lo demás: es una
    // elección explícita de la persona.
    tarjetaNavegada.click();
    return;
  }

  let producto: Producto | null = null;
  let multiplicador: number | null = null;

  if (!inputBuscarProducto.dataset.id) {
    const texto = inputBuscarProducto.value.trim();
    if (!texto) return;

    // Primero el código de barras, que es el camino del lector del mostrador
    // y tiene que seguir siendo instantáneo y exacto. Callado: si no está,
    // todavía queda la grilla.
    const resultado = await procesarCodigoEscaneado(texto, false);

    if (resultado) {
      producto = resultado.producto;
      multiplicador = resultado.multiplicador;
    } else {
      // No era un código. Si lo que se tipeó deja UN solo producto a la vista,
      // ese es. Si deja varios, no se elige por la persona: se le pide que
      // marque cuál, que es mucho mejor que cargar el equivocado.
      const visibles = navegacionTeclado.tarjetasVisibles();
      if (visibles.length === 1) {
        visibles[0].click();
        return;
      }
      if (visibles.length > 1) {
        mostrarToast(`Hay ${visibles.length} productos que coinciden: elegí con ↓ o tocá uno`, true);
        return;
      }
      mostrarToast("Producto no encontrado", true);
      inputBuscarProducto.select();
      return;
    }
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
  confirmarVenta();
});

// El escaneo por cámara está desactivado: en el mostrador se usa el lector de
// códigos físico, que entra por el campo de búsqueda. El código se deja detrás
// de esta bandera en vez de comentado — comentado, TypeScript marcaba como no
// usadas todas sus variables y esos avisos tapaban los errores de verdad.
const ESCANER_POR_CAMARA_ACTIVO = false;

if (ESCANER_POR_CAMARA_ACTIVO) {
  btnScanCamara.addEventListener("click", abrirScanner);
  btnCerrarScan.addEventListener("click", cerrarScanner);
  productosSection.classList.add("con-escaner");
}

// Filtrar productos en tiempo real
inputBuscarProducto.addEventListener("input", () => {
  const texto = inputBuscarProducto.value;
  renderProductosGrid(texto);
});

/* =======================
   INIT
======================= */
cargarProductos().then(restaurarCarritoSiHayGuardado);
cargarVentas();
getConfiguracion().then(config => {
  umbralStockBajo = config.umbralStockBajo;
  renderProductosGrid(inputBuscarProducto.value);
});
setTimeout(() => inputBuscarProducto.focus(), 300);

const { imprimirTicket } = crearImpresorTicket({ obtenerProductos: () => productos, cargarProductos });
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

/* =======================
   ESCANEAR DESDE EL CELULAR
   Ver EscaneoController/EscaneoService en el backend: la PC de mostrador
   no tiene cámara, así que el celular escanea y manda el código por SSE.
======================= */
let eventSourceEscaneo: EventSource | null = null;

// DF-UX-003: el caller (el callback de SSE, más abajo) la llama sin await
// -- es un evento asincrónico, no hay nada ahí que esperar. Sin este
// try/catch, un fallo acá (por ejemplo cargarProductos() adentro de
// procesarCodigoEscaneado si la red falla justo en ese momento) quedaba
// como una promesa rechazada sin manejar: ni toast, ni actualización del
// cartel de estado, y quien escanea desde el celular no se enteraba de nada.
async function agregarPorCodigoRemoto(codigo: string) {
  try {
    const resultado = await procesarCodigoEscaneado(codigo);
    if (!resultado) return; // procesarCodigoEscaneado ya avisó "no encontrado"

    agregarAlCarrito(resultado.producto.id!, resultado.multiplicador ?? 1);
    inputBuscarProducto.value = "";
    inputCantidad.value = "";
    delete inputBuscarProducto.dataset.id;
    productoSeleccionado = null;
    renderProductosGrid();
    mostrarToast(`${esc(resultado.producto.nombre)} agregado desde el celular`);
  } catch (error) {
    mostrarToast(
      `No se pudo agregar el producto escaneado: ${(error as Error).message || "error de red"}`,
      true
    );
  }
}

btnEscanearCelular.addEventListener("click", async () => {
  modalScanCelular.classList.remove("hidden");
  scanCelularEstado.textContent = "Generando código...";

  if (eventSourceEscaneo) {
    eventSourceEscaneo.close();
    eventSourceEscaneo = null;
  }

  try {
    const { sesionId, url } = await crearSesionDeEscaneo();

    await QRCode.toCanvas(scanCelularQr, url, { width: 260, margin: 1 });
    scanCelularEstado.textContent = "Esperando que el celular escanee un producto...";

    eventSourceEscaneo = suscribirseAEscaneo(sesionId, codigo => {
      agregarPorCodigoRemoto(codigo);
      scanCelularEstado.textContent = "¡Recibido! Seguí escaneando o cerrá esta ventana.";
    });
  } catch (error) {
    console.error(error);
    scanCelularEstado.textContent = "No se pudo generar el código. Probá de nuevo.";
  }
});

btnCerrarScanCelular.addEventListener("click", () => {
  modalScanCelular.classList.add("hidden");
  if (eventSourceEscaneo) {
    eventSourceEscaneo.close();
    eventSourceEscaneo = null;
  }
});

// Atajos de teclado del POS (B/C/flechas): pages/ventas/ventas.atajos.ts
const navegacionTeclado = crearNavegacionTeclado({ productosGrid, inputBuscarProducto, inputCantidad });

// Escape cierra el modal visible en esta pantalla (misma logica en todas las pantallas con modal).
habilitarCierreConEscape();

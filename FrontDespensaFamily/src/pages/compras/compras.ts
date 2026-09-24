import { habilitarCierreConEscape } from "../../utils/modales";
import { esc, formatearFechaHora } from "../../utils/format";
import { checkAuth } from "../../services/auth.service";
import { confirmar } from "../../services/confirm.service";
checkAuth();

import type { Compra } from "../../models/Compra";
import type { CompraRequest, DetalleCompraRequest } from "../../models/CompraRequest";
import type { Producto } from "../../models/Producto";

import {
  getCompras,
  createCompra,
  getCompraDetalle,
  anularCompra,
  marcarPagada
} from "../../services/compras.service";

import { getProductos } from "../../services/productos.service";

// ---------------- ESTADO ----------------
let compras: Compra[] = [];
let productos: Producto[] = [];
let detallesCompra: DetalleCompraRequest[] = [];

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

function formatearCantidad(cantidad: number, producto?: Producto) {
  if (!producto) return cantidad.toString();
  if (esProductoPesable(producto)) return `${cantidad.toFixed(3)} ${unidadProducto(producto)}`;
  return `${cantidad} ${unidadProducto(producto)}`;
}

function esProductoPorPaquete(producto: Producto): boolean {
  return !esProductoPesable(producto) && !!producto.unidadesPorPaquete && producto.unidadesPorPaquete >= 2;
}

function desglosePaquete(cantidad: number, producto?: Producto): { paquetes: number; sueltas: number } | null {
  const upp = producto?.unidadesPorPaquete;
  if (!producto || !upp || upp < 2 || esProductoPesable(producto)) return null;
  return { paquetes: Math.floor(cantidad / upp), sueltas: cantidad % upp };
}

function formatearCantidadConPaquete(cantidad: number, producto?: Producto): string {
  const base = formatearCantidad(cantidad, producto);
  const desglose = desglosePaquete(cantidad, producto);
  if (!desglose) return base;
  return `${base} (${desglose.paquetes} paq + ${desglose.sueltas} u)`;
}

function configurarInputCantidadCompra(producto: Producto) {
  if (esProductoPorPaquete(producto)) {
    inputCantidad.step = "1";
    inputCantidad.min = "1";
    inputCantidad.placeholder = `Cantidad de paquetes (x${producto.unidadesPorPaquete} u c/u)`;
    inputPrecioCosto.placeholder = "Precio costo por paquete";
    return;
  }

  const step = fraccionMinima(producto);
  inputCantidad.step = step.toString();
  inputCantidad.min = step.toString();
  inputCantidad.placeholder = esProductoPesable(producto)
    ? `Cantidad en ${unidadProducto(producto)}`
    : "Cantidad en unidades";
  inputPrecioCosto.placeholder = "Precio costo";
}

// ---------------- DOM ----------------
const formCompra = document.getElementById("form-compra") as HTMLFormElement;

const inputProveedor = document.getElementById("proveedor") as HTMLInputElement;
const inputBuscarProducto = document.getElementById("input-buscar-producto") as HTMLInputElement;
const dropdownProductos = document.getElementById("dropdown-productos") as HTMLDivElement;
const inputCantidad = document.getElementById("cantidad") as HTMLInputElement;
const inputPrecioCosto = document.getElementById("precioCosto") as HTMLInputElement;

const btnAgregar = document.getElementById("btn-agregar") as HTMLButtonElement;
const tablaDetalles = document.getElementById("tabla-detalles") as HTMLTableElement;
const toast = document.getElementById("toast") as HTMLDivElement;

const checkboxPagada = document.getElementById("checkbox-pagada") as HTMLInputElement;
const selectMetodoPagoCompra = document.getElementById("select-metodo-pago-compra") as HTMLSelectElement;

const modalRegistrarCompra = document.getElementById("modal-registrar-compra") as HTMLDivElement;
const btnAbrirRegistrarCompra = document.getElementById("btn-abrir-registrar-compra") as HTMLButtonElement;
const btnCerrarRegistrarCompra = document.getElementById("btn-cerrar-registrar-compra") as HTMLButtonElement;

// ---------------- FUNCIONES ----------------

function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden", "error");
  if (error) toast.classList.add("error");
  setTimeout(() => toast.classList.add("hidden"), 3000);
}

// Cargar compras
async function cargarCompras() {
  try {
    compras = await getCompras();
    const comprasOrdenadas = [...compras].reverse();
    renderCompras(comprasOrdenadas);
  } catch (error) {
    console.error(error);
    mostrarToast("Error al cargar compras", true);
  }
}

// Render compras
function renderCompras(lista: Compra[]) {
    const tbody = document.querySelector("#tabla-compras tbody")!;
    tbody.innerHTML = "";

    lista.forEach(compra => {
        const anulada = compra.estado === "ANULADA";

        const tr = document.createElement("tr");
        if (anulada) tr.classList.add("venta-anulada");
        // Una compra a cuenta no sacó plata de caja todavía (ver
        // CompraService, Fase 11): la diferenciación visual es a propósito,
        // mismo criterio que ingreso/egreso en la pantalla de Caja.
        const pendiente = !anulada && compra.pagada === false;

        tr.innerHTML = `
            <td>${compra.id}</td>
            <td>${esc(compra.proveedor)}</td>
            <td>${formatearFechaHora(compra.fechaHora)}</td>
            <td>$${(compra.total ?? 0).toFixed(2)}</td>
            <td>
              ${pendiente
                ? '<span class="etiqueta-pendiente">A cuenta</span>'
                : '<span class="etiqueta-pagada">Pagada</span>'}
            </td>
            <td>${esc(compra.usuario) || "—"}</td>
            <td class="compras-acciones">
              ${anulada
                ? `<span class="etiqueta-anulada" title="Anulada por ${esc(compra.anuladaPor) || "—"}">Anulada</span>`
                : `<button class="btn-anular-compra" data-id="${compra.id}">Anular</button>
                   ${pendiente ? `<button class="btn-marcar-pagada" data-id="${compra.id}">Marcar pagada</button>` : ""}`}
            </td>
        `;
        tbody.appendChild(tr);

        tr.addEventListener("click", () => {
            verDetalleCompra(compra.id!);
        });

        // Una compra mal cargada infla el stock y reescribe el precio del
        // producto: anularla deshace las dos cosas.
        const btnAnular = tr.querySelector(".btn-anular-compra") as HTMLButtonElement | null;
        btnAnular?.addEventListener("click", async (e) => {
            e.stopPropagation();
            const confirmado = await confirmar(
                "Se va a descontar el stock que sumó esta compra y volver al precio de costo anterior. ¿Anular la compra?");
            if (!confirmado) return;

            try {
                await anularCompra(compra.id!);
                mostrarToast("Compra anulada");
                cargarCompras();
            } catch (err) {
                mostrarToast((err as Error).message, true);
            }
        });

        const btnMarcarPagada = tr.querySelector(".btn-marcar-pagada") as HTMLButtonElement | null;
        btnMarcarPagada?.addEventListener("click", async (e) => {
            e.stopPropagation();
            const confirmado = await confirmar(
                `Se va a registrar un egreso de $${(compra.total ?? 0).toFixed(2)} en caja, con fecha de hoy. ¿Marcar como pagada?`);
            if (!confirmado) return;

            try {
                await marcarPagada(compra.id!);
                mostrarToast("Compra marcada como pagada");
                cargarCompras();
            } catch (err) {
                mostrarToast((err as Error).message, true);
            }
        });
    });

// Agregar eventos a los botones despues de renderizar
    document.querySelectorAll(".btn-detalle").forEach(btn => {
        btn.addEventListener("click", () => {
            const id = Number((btn as HTMLElement).dataset.id);
            verDetalleCompra(id);
        });
    });

  }


// Cargar productos
async function cargarProductos() {
  try {
    productos = await getProductos();
  } catch (error) {
    console.error(error);
    mostrarToast("Error al cargar productos", true);
  }
}

// ---------------- BUSCADOR DE PRODUCTOS (reemplaza el <select> nativo) ----------------
function renderDropdownProductos(filtro: string) {
  const texto = filtro.toLowerCase().trim();
  if (!texto) {
    dropdownProductos.classList.add("hidden");
    dropdownProductos.innerHTML = "";
    return;
  }

  const coincidencias = productos.filter(p =>
    p.nombre.toLowerCase().includes(texto) ||
    p.codigoBarras?.toLowerCase().includes(texto)
  ).slice(0, 50); // tope razonable de sugerencias visibles, no hace falta más

  dropdownProductos.innerHTML = "";

  if (coincidencias.length === 0) {
    dropdownProductos.innerHTML = `<div class="dropdown-sin-resultados">No se encontraron productos</div>`;
  } else {
    coincidencias.forEach(p => {
      const item = document.createElement("div");
      item.className = "dropdown-item";
      item.textContent = p.nombre;
      item.addEventListener("click", () => seleccionarProductoCompra(p));
      dropdownProductos.appendChild(item);
    });
  }

  dropdownProductos.classList.remove("hidden");
}

function seleccionarProductoCompra(producto: Producto) {
  inputBuscarProducto.value = producto.nombre;
  inputBuscarProducto.dataset.id = producto.id!.toString();
  configurarInputCantidadCompra(producto);
  inputCantidad.value = fraccionMinima(producto).toString();
  dropdownProductos.classList.add("hidden");
  dropdownProductos.innerHTML = "";
  inputCantidad.focus();
}

// Agregar detalle
btnAgregar.addEventListener("click", () => {
  const productoId = Number(inputBuscarProducto.dataset.id);
  const cantidadIngresada = Number(inputCantidad.value);
  const precioIngresado = Number(inputPrecioCosto.value);
  const producto = productos.find(p => p.id === productoId);

  if (!productoId || cantidadIngresada <= 0 || precioIngresado <= 0 || !producto) {
    mostrarToast("Datos inválidos: el precio de costo debe ser mayor a 0", true);
    return;
  }

  if (!esProductoPesable(producto) && !Number.isInteger(cantidadIngresada)) {
    mostrarToast("Este producto se compra por unidad, la cantidad debe ser entera", true);
    return;
  }

  // Si el producto se compra por paquete, acá adentro se ingresan
  // paquetes y costo-por-paquete; se convierte a unidades sueltas
  // y costo-por-unidad antes de guardar (el backend no cambia).
  let cantidad = cantidadIngresada;
  let precioCosto = precioIngresado;
  if (esProductoPorPaquete(producto)) {
    cantidad = cantidadIngresada * producto.unidadesPorPaquete!;
    precioCosto = precioIngresado / producto.unidadesPorPaquete!;
  }

  detallesCompra.push({ productoId, cantidad, precioCosto });
  renderDetalles();

  inputBuscarProducto.value = "";
  delete inputBuscarProducto.dataset.id;
  inputCantidad.value = "";
  inputPrecioCosto.value = "";
  inputBuscarProducto.focus();
});

// Render detalles
function renderDetalles() {
  const tbody = tablaDetalles.querySelector("tbody")!;
  tbody.innerHTML = "";

  detallesCompra.forEach((d, index) => {
    const producto = productos.find(p => p.id === d.productoId);

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <!-- DF-SEC-001: nombre del producto, escrito por quien lo carga. -->
      <td>${producto ? esc(producto.nombre) : "Producto"}</td>
      <td>${formatearCantidadConPaquete(d.cantidad, producto)}</td>
      <td>$${d.precioCosto.toFixed(2)} / ${producto ? unidadProducto(producto) : "u"}</td>
      <td>${(d.cantidad * d.precioCosto).toFixed(2)}</td>
      <td>
        <button data-index="${index}" class="btn-quitar">Quitar</button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  document.querySelectorAll(".btn-quitar").forEach(btn => {
    btn.addEventListener("click", () => {
      const index = Number((btn as HTMLElement).dataset.index);
      detallesCompra.splice(index, 1);
      renderDetalles();
    });
  });
}

// Registrar compra
const btnGuardarCompra = formCompra.querySelector(".btn-guardar") as HTMLButtonElement;
const textoOriginalBtnGuardar = btnGuardarCompra.textContent || "Registrar Compra";

formCompra.addEventListener("submit", async e => {
  e.preventDefault();

  const proveedor = inputProveedor.value.trim();

  if (!proveedor) {
    mostrarToast("El proveedor es obligatorio", true);
    return;
  }

  if (detallesCompra.length === 0) {
    mostrarToast("Debe agregar al menos un producto", true);
    return;
  }

  const compra: CompraRequest = {
    proveedor,
    detalles: detallesCompra,
    pagada: checkboxPagada.checked,
    metodoPago: selectMetodoPagoCompra.value as CompraRequest["metodoPago"]
  };

  btnGuardarCompra.disabled = true;
  btnGuardarCompra.textContent = "Guardando...";

  try {
    await createCompra(compra);
    mostrarToast("Compra registrada correctamente");

    detallesCompra = [];
    renderDetalles();
    formCompra.reset();
    cargarCompras();
    cerrarModalRegistrarCompra();
  } catch (error) {
    console.error(error);
    mostrarToast("Error al registrar compra", true);
  } finally {
    btnGuardarCompra.disabled = false;
    btnGuardarCompra.textContent = textoOriginalBtnGuardar;
  }
});

// ---------------- MODAL REGISTRAR COMPRA ----------------
function abrirModalRegistrarCompra() {
  modalRegistrarCompra.classList.remove("hidden");
  inputProveedor.focus();
}

function cerrarModalRegistrarCompra() {
  modalRegistrarCompra.classList.add("hidden");
}

btnAbrirRegistrarCompra.addEventListener("click", abrirModalRegistrarCompra);
btnCerrarRegistrarCompra.addEventListener("click", cerrarModalRegistrarCompra);

// Cerrar con click en el fondo (fuera del contenido del modal)
modalRegistrarCompra.addEventListener("click", (e) => {
  if (e.target === modalRegistrarCompra) cerrarModalRegistrarCompra();
});

// Cerrar con ESC, salvo que el dropdown de productos esté abierto (ahí ESC
// cierra primero el dropdown, como se espera de un autocompletado)
document.addEventListener("keydown", (e) => {
  if (e.key !== "Escape" || modalRegistrarCompra.classList.contains("hidden")) return;
  if (!dropdownProductos.classList.contains("hidden")) {
    dropdownProductos.classList.add("hidden");
    return;
  }
  cerrarModalRegistrarCompra();
});

// ---------------- INIT ----------------
cargarCompras();
cargarProductos();
// ---------------- DOM modal ----------------
const modalDetalleCompra = document.getElementById("modal-detalle-compra") as HTMLDivElement;
const detalleCompraId = document.getElementById("detalle-compra-id") as HTMLSpanElement;
const detalleCompraProveedor = document.getElementById("detalle-compra-proveedor") as HTMLSpanElement;
const detalleCompraFecha = document.getElementById("detalle-compra-fecha") as HTMLSpanElement;
const detalleCompraPago = document.getElementById("detalle-compra-pago") as HTMLSpanElement;
const tablaDetalleCompra = document.getElementById("tabla-detalle-compra") as HTMLTableSectionElement;
const detalleCompraTotal = document.getElementById("detalle-compra-total") as HTMLSpanElement;
const btnCerrarDetalleCompra = document.getElementById("btn-cerrar-detalle-compra") as HTMLButtonElement;

// ---------------- Ver detalle de compra (modal) ----------------
async function verDetalleCompra(id: number) {
    const compra = await getCompraDetalle(id);

    detalleCompraId.textContent = compra.id.toString();
    detalleCompraProveedor.textContent = compra.proveedor;
    detalleCompraFecha.textContent = formatearFechaHora(compra.fechaHora);
    detalleCompraPago.textContent = compra.pagada === false
        ? `A cuenta (método previsto: ${compra.metodoPago ?? "—"})`
        : `Pagada — ${compra.metodoPago ?? "—"}`;

    tablaDetalleCompra.innerHTML = "";

    compra.detalles.forEach(det => {
    const subtotal = det.cantidad * det.precioCosto;

    const producto = productos.find(p => p.id === det.productoId);
    const nombre = producto ? producto.nombre : `Producto ${det.productoId}`;

    const tr = document.createElement("tr");
    tr.innerHTML = `
        <td>${esc(nombre)}</td>
        <td>${formatearCantidadConPaquete(det.cantidad, producto)}</td>
        <td>$${det.precioCosto.toFixed(2)} / ${producto ? unidadProducto(producto) : "u"}</td>
        <td>$${subtotal.toFixed(2)}</td>
    `;
    tablaDetalleCompra.appendChild(tr);
});


    // Congelado en el servidor (Compra.total) y no la suma de subtotales que
    // se ve acá: si el redondeo cambia en el futuro, el total de una compra
    // vieja no debe cambiar con retroactividad.
    detalleCompraTotal.textContent = compra.total.toFixed(2);

    // Mostrar modal
    modalDetalleCompra.classList.remove("hidden");
}
//------------- Cerrar modal ----------------
btnCerrarDetalleCompra.addEventListener("click", () => {
    modalDetalleCompra.classList.add("hidden");
});

inputBuscarProducto.addEventListener("input", () => {
  // Escribir de nuevo invalida la selección anterior hasta que se elija
  // un ítem de la lista otra vez.
  delete inputBuscarProducto.dataset.id;
  renderDropdownProductos(inputBuscarProducto.value);
});

inputBuscarProducto.addEventListener("focus", () => {
  if (inputBuscarProducto.value.trim()) renderDropdownProductos(inputBuscarProducto.value);
});

document.addEventListener("click", (e) => {
  const target = e.target as HTMLElement;
  if (!target.closest(".producto-buscar-container")) {
    dropdownProductos.classList.add("hidden");
  }
});


// Escape cierra el modal visible en esta pantalla (misma logica en todas las pantallas con modal).
habilitarCierreConEscape();

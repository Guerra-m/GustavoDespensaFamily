import { checkAuth } from "../../services/auth.service";
checkAuth();

import type { Producto } from "../../models/Producto";
import type { Categoria } from "../../models/Categoria";

import {
  getProductos,
  createProducto,
  updateProducto,
  deleteProducto
} from "../../services/productos.service";

import { getCategorias } from "../../services/categorias.service";
import { confirmar } from "../../services/confirm.service";
import { formatearCantidad } from "../../utils/format";

// ---------- ELEMENTOS DEL DOM ----------
const productosGrid = document.getElementById("productos-grid") as HTMLDivElement;
const productosGridWrapper = document.getElementById("productos-grid-wrapper") as HTMLDivElement | null;
const indiceCategorias = document.getElementById("productos-indice") as HTMLDivElement;
const toast = document.getElementById("toast") as HTMLDivElement;

const formCrear = document.getElementById("form-crear") as HTMLFormElement;
const formEditar = document.getElementById("form-editar") as HTMLFormElement;
const inputBuscar = document.getElementById("input-buscar") as HTMLInputElement;

const selectCrear = formCrear.querySelector(
  "select[name='categoriaId']"
) as HTMLSelectElement;

const selectEditar = formEditar.querySelector(
  "select[name='categoriaId']"
) as HTMLSelectElement;

const modal = document.getElementById("modal-editar") as HTMLDivElement;
const btnCancelar = document.getElementById("btn-cancelar") as HTMLButtonElement;

const modalCrear = document.getElementById("modal-crear") as HTMLDivElement;
const btnCrearProducto = document.getElementById("btn-crear-producto") as HTMLButtonElement;
const btnCerrarCrear = document.getElementById("btn-cerrar-crear") as HTMLButtonElement;

const barraSeleccion = document.getElementById("barra-seleccion") as HTMLDivElement;
const contadorSeleccion = document.getElementById("contador-seleccion") as HTMLSpanElement;
const btnImprimirSeleccionados = document.getElementById("btn-imprimir-seleccionados") as HTMLButtonElement;
const btnCancelarSeleccion = document.getElementById("btn-cancelar-seleccion") as HTMLButtonElement;
const btnModoSeleccion = document.getElementById("btn-modo-seleccion") as HTMLButtonElement;

// ---------- INPUTS PRECIO ----------
const inputCostoCrear = formCrear.querySelector(
  "input[name='precioCosto']"
) as HTMLInputElement;

const inputVentaCrear = formCrear.querySelector(
  "input[name='precioVenta']"
) as HTMLInputElement;

const inputCostoEditar = formEditar.querySelector(
  "input[name='precioCosto']"
) as HTMLInputElement;

const inputVentaEditar = formEditar.querySelector(
  "input[name='precioVenta']"
) as HTMLInputElement;

const inputPorcentajeCrear = formCrear.querySelector(
  "input[name='porcentajeGanancia']"
) as HTMLInputElement;

const inputPorcentajeEditar = formEditar.querySelector(
  "input[name='porcentajeGanancia']"
) as HTMLInputElement;

const textoGananciaCrear = formCrear.querySelector(
  ".valor-ganancia"
) as HTMLSpanElement;

const textoGananciaEditar = formEditar.querySelector(
  ".valor-ganancia"
) as HTMLSpanElement;

// ---------- ESTADO ----------
let productos: Producto[] = [];
let categorias: Categoria[] = [];
let productoActual: Producto | null = null;
let seleccionados = new Set<number>();

// ---------- UTIL ----------
function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  toast.classList.toggle("error", error);
  setTimeout(() => toast.classList.add("hidden"), 2500);
}

function calcularPrecioVenta(precioCosto: number, porcentaje: number): number {
  return +(precioCosto + (precioCosto * porcentaje) / 100).toFixed(2);
}

function esProductoPesable(producto: Producto): boolean {
  return producto.tipoCantidad === "PESO";
}

function obtenerUnidad(producto: Producto): "u" | "kg" {
  return producto.unidadMedida || (esProductoPesable(producto) ? "kg" : "u");
}

function obtenerFraccionMinima(producto: Producto): number {
  if (producto.fraccionMinima && producto.fraccionMinima > 0) return producto.fraccionMinima;
  return esProductoPesable(producto) ? 0.001 : 1;
}

function sincronizarTipoYUnidad(form: HTMLFormElement) {
  const tipo = form.querySelector("select[name='tipoCantidad']") as HTMLSelectElement;
  const unidad = form.querySelector("select[name='unidadMedida']") as HTMLSelectElement;
  const fraccion = form.querySelector("input[name='fraccionMinima']") as HTMLInputElement;
  const stock = form.querySelector("input[name='stock']") as HTMLInputElement;
  const campoPaquete = form.querySelector(".campo-paquete") as HTMLLabelElement | null;
  const unidadesPorPaquete = form.querySelector("input[name='unidadesPorPaquete']") as HTMLInputElement;

  const aplicarValores = () => {
    if (tipo.value === "PESO") {
      unidad.value = "kg";
      fraccion.value = fraccion.value && Number(fraccion.value) < 1 ? fraccion.value : "0.001";
      fraccion.step = "0.001";
      fraccion.min = "0.001";
      stock.step = "0.001";
      stock.min = "0.001";
      // El empaquetado solo tiene sentido para productos por unidad
      if (campoPaquete) campoPaquete.classList.add("hidden");
      unidadesPorPaquete.value = "";
    } else {
      unidad.value = "u";
      fraccion.value = "1";
      fraccion.step = "1";
      fraccion.min = "1";
      stock.step = "1";
      stock.min = "0";
      if (campoPaquete) campoPaquete.classList.remove("hidden");
    }
  };

  tipo.addEventListener("change", aplicarValores);
  aplicarValores();
}

// ---------- DESGLOSE PAQUETE/SUELTAS ----------
function desglosePaquete(cantidad: number, producto?: Producto): { paquetes: number; sueltas: number } | null {
  const upp = producto?.unidadesPorPaquete;
  if (!upp || upp < 2 || esProductoPesable(producto!)) return null;
  return { paquetes: Math.floor(cantidad / upp), sueltas: cantidad % upp };
}

function formatearStockConPaquete(producto: Producto): string {
  const base = `${formatearCantidad(producto.stock)} ${obtenerUnidad(producto)}`;
  const desglose = desglosePaquete(producto.stock, producto);
  if (!desglose) return base;
  return `${base} (${desglose.paquetes} paq + ${desglose.sueltas} u)`;
}

// ---------- AUTOCÁLCULO CREAR ----------
inputCostoCrear.addEventListener("input", () =>
  recalcularVenta(
    inputCostoCrear,
    inputPorcentajeCrear,
    inputVentaCrear,
    textoGananciaCrear
  )
);

inputPorcentajeCrear.addEventListener("input", () =>
  recalcularVenta(
    inputCostoCrear,
    inputPorcentajeCrear,
    inputVentaCrear,
    textoGananciaCrear
  )
);

// ---------- AUTOCÁLCULO EDITAR ----------
inputCostoEditar.addEventListener("input", () =>
  recalcularVenta(
    inputCostoEditar,
    inputPorcentajeEditar,
    inputVentaEditar,
    textoGananciaEditar
  )
);

inputPorcentajeEditar.addEventListener("input", () =>
  recalcularVenta(
    inputCostoEditar,
    inputPorcentajeEditar,
    inputVentaEditar,
    textoGananciaEditar
  )
);

// ---------- CREAR CARD ----------
function crearCard(prod: Producto): HTMLDivElement {
  const card = document.createElement("div");
  card.className = "producto-card";
  if (prod.stock < 10) card.classList.add("stock-bajo");

  card.innerHTML = `
    <label class="producto-check">
      <input type="checkbox" class="chk-seleccionar" data-id="${prod.id}" ${seleccionados.has(prod.id!) ? "checked" : ""} title="Seleccionar para imprimir cartel">
    </label>
    <div class="producto-acciones">
      <button class="btn-editar-rapido" data-id="${prod.id}" title="Editar"><span class="icon" style="--icono:url('/icons/pencil.svg')"></span></button>
      <div class="producto-menu-container">
        <button class="btn-menu" data-id="${prod.id}" aria-label="Más opciones" title="Más opciones"></button>
        <div class="producto-menu-dropdown">
          <button class="btn-imprimir-menu" data-id="${prod.id}">Imprimir cartel</button>
          <button class="btn-eliminar-menu" data-id="${prod.id}">Eliminar</button>
        </div>
      </div>
    </div>
    <div class="producto-imagen">
      ${prod.imagenUrl ? `<img src="${prod.imagenUrl}" alt="${prod.nombre}" loading="lazy">` : ''}
    </div>
    <div class="producto-info">
      <div class="producto-nombre">${prod.nombre}</div>
      <div class="producto-precio">$${prod.precioVenta.toFixed(2)} / ${obtenerUnidad(prod)}</div>
      <div class="producto-stock">Stock: ${formatearStockConPaquete(prod)}</div>
    </div>
  `;
  return card;
}

// ---------- CARTEL DE PRECIO (ETIQUETA ESTILO GRILLA SIMPLE) ----------
function construirEtiquetaHtml(prod: Producto): string {
  const precio = prod.precioVenta.toFixed(2).replace(".", ",");

  return `
    <div class="etiqueta">
      <div class="etiqueta-nombre">${prod.nombre}</div>
      <div class="etiqueta-precio">${precio}</div>
      ${prod.codigoBarras ? `<div class="etiqueta-codigo">${prod.codigoBarras}</div>` : ""}
    </div>
  `;
}

const ESTILOS_ETIQUETAS = `
  * { box-sizing: border-box; margin: 0; padding: 0; }
  html, body {
    font-family: 'Segoe UI', Arial, sans-serif;
    background: #fff;
    print-color-adjust: exact;
    -webkit-print-color-adjust: exact;
  }
  body { padding: 8mm; }
  .hoja-etiquetas {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
  }
  .etiqueta {
    border: 1px dashed #cbd5e1;
    min-height: 32mm;
    padding: 12px 8px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    text-align: center;
    break-inside: avoid;
    page-break-inside: avoid;
  }
  .etiqueta-nombre {
    font-size: 13px;
    font-weight: 600;
    color: #111;
    line-height: 1.3;
    word-break: break-word;
  }
  .etiqueta-precio {
    font-size: 32px;
    font-weight: 800;
    color: #111;
    line-height: 1;
  }
  .etiqueta-codigo {
    font-size: 11px;
    color: #6b7280;
    letter-spacing: 0.5px;
  }
  @media print {
    @page { margin: 10mm; size: A4 portrait; }
    body { padding: 0; }
  }
`;

function imprimirEtiquetas(lista: Producto[]) {
  if (lista.length === 0) return;

  const ventana = window.open("", "_blank");
  if (!ventana) return;

  const etiquetasHtml = lista.map(construirEtiquetaHtml).join("");

  ventana.document.write(`<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Carteles de precio</title>
  <style>${ESTILOS_ETIQUETAS}</style>
</head>
<body>
  <div class="hoja-etiquetas">
    ${etiquetasHtml}
  </div>
</body>
</html>`);

  ventana.document.close();
  ventana.print();
}

// ---------- SELECCIÓN DE PRODUCTOS PARA IMPRIMIR ----------
function actualizarBarraSeleccion() {
  const cantidad = seleccionados.size;
  contadorSeleccion.textContent = `${cantidad} seleccionado${cantidad === 1 ? "" : "s"}`;
  barraSeleccion.classList.toggle("hidden", cantidad === 0);
}

function limpiarSeleccion() {
  seleccionados.clear();
  productosGrid.querySelectorAll<HTMLInputElement>(".chk-seleccionar").forEach(chk => {
    chk.checked = false;
  });
  actualizarBarraSeleccion();
}

function salirModoSeleccion() {
  productosGrid.classList.remove("modo-seleccion");
  btnModoSeleccion.classList.remove("activo");
  btnModoSeleccion.innerHTML = `<span class="icon" style="--icono:url('/icons/printer.svg')"></span> Seleccionar para imprimir`;
  limpiarSeleccion();
}

btnModoSeleccion.addEventListener("click", () => {
  const activando = !productosGrid.classList.contains("modo-seleccion");
  if (activando) {
    productosGrid.classList.add("modo-seleccion");
    btnModoSeleccion.classList.add("activo");
    btnModoSeleccion.textContent = "Cancelar selección";
  } else {
    salirModoSeleccion();
  }
});

// ---------- RENDER GRID CON SECCIONES POR CATEGORÍA ----------
function renderProductos(lista: Producto[]) {
  productosGrid.innerHTML = "";

  if (lista.length === 0) {
    productosGrid.innerHTML = `<div class="prod-sin-resultados">No se encontraron productos</div>`;
    return;
  }

  const grupos = new Map<string, { nombre: string; prods: Producto[] }>();

  lista.forEach(prod => {
    const cat = categorias.find(c => c.id === prod.categoriaId);
    const key = cat ? String(cat.id) : "__sin__";
    const nombre = cat ? cat.nombre : "Sin categoría";
    if (!grupos.has(key)) grupos.set(key, { nombre, prods: [] });
    grupos.get(key)!.prods.push(prod);
  });

  const gruposOrdenados = [...grupos.entries()]
    .sort(([, a], [, b]) => {
      if (a.nombre === "Sin categoría") return 1;
      if (b.nombre === "Sin categoría") return -1;
      return a.nombre.localeCompare(b.nombre, "es");
    });

  gruposOrdenados.forEach(([key, { nombre, prods }]) => {
    const seccion = document.createElement("div");
    seccion.className = "prod-cat-seccion";
    seccion.id = `cat-${key}`;

    const titulo = document.createElement("div");
    titulo.className = "prod-cat-titulo";
    titulo.textContent = nombre;
    seccion.appendChild(titulo);

    const grid = document.createElement("div");
    grid.className = "prod-cat-grid";
    prods.forEach(prod => grid.appendChild(crearCard(prod)));
    seccion.appendChild(grid);

    productosGrid.appendChild(seccion);
  });

  renderIndiceCategorias(gruposOrdenados.map(([key, { nombre }]) => ({ key, nombre })));
}

// ---------- ÍNDICE POR CATEGORÍA (scroll-spy) ----------
// Barra lateral fija con las categorías presentes en el listado actual;
// se resalta sola la categoría que está a la vista mientras se scrollea
// la grilla, para no perder de vista "por dónde vas" en un catálogo de
// miles de productos.
let observerIndice: IntersectionObserver | null = null;

function renderIndiceCategorias(items: { key: string; nombre: string }[]) {
  if (!indiceCategorias) return;
  indiceCategorias.innerHTML = "";

  items.forEach(({ key, nombre }) => {
    const link = document.createElement("button");
    link.type = "button";
    link.className = "indice-cat-link";
    link.textContent = nombre;
    link.dataset.target = `cat-${key}`;
    link.addEventListener("click", () => {
      document.getElementById(`cat-${key}`)?.scrollIntoView({ behavior: "smooth", block: "start" });
    });
    indiceCategorias.appendChild(link);
  });

  observerIndice?.disconnect();
  if (!productosGridWrapper || items.length === 0) return;

  observerIndice = new IntersectionObserver(
    (entries) => {
      // De las secciones visibles, la más arriba dentro del scroll marca
      // la categoría "activa" del momento.
      const visibles = entries
        .filter(e => e.isIntersecting)
        .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
      if (visibles.length === 0) return;

      const idActivo = visibles[0].target.id;
      indiceCategorias.querySelectorAll<HTMLButtonElement>(".indice-cat-link").forEach(link => {
        link.classList.toggle("activo", link.dataset.target === idActivo);
      });
    },
    { root: productosGridWrapper, rootMargin: "0px 0px -70% 0px", threshold: 0 }
  );

  document.querySelectorAll(".prod-cat-seccion").forEach(sec => observerIndice!.observe(sec));
}

// ---------- DELEGACIÓN DE EVENTOS PARA EL MENÚ ----------
productosGrid.addEventListener("click", (e) => {
  const target = e.target as HTMLElement;

  // BOTÓN EDITAR (visible directo en la card, no adentro del menú de 3
  // puntos — es la acción más frecuente, no tiene sentido esconderla)
  if (target.closest('.btn-editar-rapido')) {
    const btn = target.closest('.btn-editar-rapido') as HTMLElement;
    const id = Number(btn.dataset.id);
    abrirEditarProducto(id);
  }

  // BOTÓN IMPRIMIR CARTEL
  if (target.closest('.btn-imprimir-menu')) {
    const btn = target.closest('.btn-imprimir-menu') as HTMLElement;
    const id = Number(btn.dataset.id);
    cerrarTodosLosMenus();
    const prod = productos.find(p => p.id === id);
    if (prod) imprimirEtiquetas([prod]);
  }

  // BOTÓN ELIMINAR
  if (target.closest('.btn-eliminar-menu')) {
    const btn = target.closest('.btn-eliminar-menu') as HTMLElement;
    const id = Number(btn.dataset.id);
    cerrarTodosLosMenus();
    eliminarProducto(id);
  }

  // BOTÓN MENU (tres puntos)
  if (target.closest('.btn-menu')) {
    const btn = target.closest('.btn-menu') as HTMLElement;
    const dropdown = btn.nextElementSibling as HTMLDivElement;

    // Cerrar otros menús abiertos
    document.querySelectorAll('.producto-menu-dropdown.active').forEach(menu => {
      if (menu !== dropdown) menu.classList.remove('active');
    });

    // Toggle este menú
    dropdown.classList.toggle('active');
  }
});

// CHECKBOX DE SELECCIÓN PARA IMPRIMIR CARTELES
productosGrid.addEventListener("change", (e) => {
  const target = e.target as HTMLElement;
  if (!target.classList.contains("chk-seleccionar")) return;

  const chk = target as HTMLInputElement;
  const id = Number(chk.dataset.id);

  if (chk.checked) {
    seleccionados.add(id);
  } else {
    seleccionados.delete(id);
  }

  actualizarBarraSeleccion();
});

// BARRA DE SELECCIÓN: IMPRIMIR CARTELES SELECCIONADOS
btnImprimirSeleccionados.addEventListener("click", () => {
  const lista = productos.filter(p => seleccionados.has(p.id!));
  imprimirEtiquetas(lista);
  salirModoSeleccion();
});

// BARRA DE SELECCIÓN: CANCELAR
btnCancelarSeleccion.addEventListener("click", () => {
  salirModoSeleccion();
});

// Cerrar cualquier menú abierto al clickear fuera de él
document.addEventListener("click", (e) => {
  const target = e.target as HTMLElement;
  if (!target.closest('.producto-menu-container')) {
    cerrarTodosLosMenus();
  }
});

// ---------- CERRAR TODOS LOS MENÚS ----------
function cerrarTodosLosMenus() {
  document.querySelectorAll('.producto-menu-dropdown.active').forEach(menu => {
    menu.classList.remove('active');
  });
}

// ---------- EVENTOS DEL MENÚ ----------
function agregarEventosMenu() {
  // Crear overlay para cerrar menús SI NO EXISTE
  let overlay = document.querySelector('.producto-menu-overlay') as HTMLDivElement;
  if (!overlay) {
    overlay = document.createElement('div');
    overlay.className = 'producto-menu-overlay';
    document.body.appendChild(overlay);
    
    // Evento del overlay (solo una vez)
   overlay.addEventListener('click', (e) => {
  const target = e.target as HTMLElement;

  // Si el clic NO es dentro de ningún dropdown, cerramos
  if (!target.closest('.producto-menu-dropdown') && !target.closest('.btn-menu')) {
    cerrarTodosLosMenus();
  }
});
  }

  // Botones de menú (tres puntos)
  document.querySelectorAll('.btn-menu').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      
      // Cerrar otros menús abiertos
      document.querySelectorAll('.producto-menu-dropdown.active').forEach(menu => {
        if (menu !== btn.nextElementSibling) {
          menu.classList.remove('active');
        }
      });
      
      // Toggle este menú
      const dropdown = btn.nextElementSibling as HTMLDivElement;
      const estaActivo = dropdown.classList.contains('active');
      
      if (estaActivo) {
        dropdown.classList.remove('active');
        overlay.classList.remove('active');
      } else {
        dropdown.classList.add('active');
        overlay.classList.add('active');
      }
    });
  });

  // Botones de editar en el menú
  document.querySelectorAll('.btn-editar-menu').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      const id = Number((btn as HTMLElement).dataset.id);
      cerrarTodosLosMenus();
      abrirEditarProducto(id);
    });
  });

  // Botones de eliminar en el menú
  document.querySelectorAll('.btn-eliminar-menu').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
      const id = Number((btn as HTMLElement).dataset.id);
      cerrarTodosLosMenus();
      eliminarProducto(id);
    });
  });
}

// ---------- CERRAR MENÚS ----------


// ---------- CARGAR CATEGORÍAS ----------
async function cargarCategorias() {
  categorias = await getCategorias();
  categorias.sort((a, b) => a.nombre.localeCompare(b.nombre));

  selectCrear.innerHTML = `<option value="">Seleccione categoría</option>`;
  selectEditar.innerHTML = `<option value="">Seleccione categoría</option>`;

  categorias.forEach(cat => {
    selectCrear.add(new Option(cat.nombre, cat.id.toString()));
    selectEditar.add(new Option(cat.nombre, cat.id.toString()));
  });
}

// ---------- CARGAR PRODUCTOS ----------
async function cargarProductos() {
  productos = await getProductos();
  // Reaplica el filtro de búsqueda actual en vez de mostrar siempre todo:
  // antes, crear/editar/eliminar volvía a traer los productos y perdía el
  // texto ya tipeado en el buscador (la lista volvía a verse completa sin
  // avisar, aunque el input seguía mostrando el texto de búsqueda).
  aplicarFiltro();
}

// ---------- BUSCAR (filtra sobre el estado actual de `productos`) ----------
function aplicarFiltro() {
  const q = inputBuscar.value.toLowerCase().trim();

  if (!q) {
    renderProductos([...productos].sort((a, b) => a.nombre.localeCompare(b.nombre)));
    return;
  }

  renderProductos(
    productos.filter(p =>
      p.nombre.toLowerCase().includes(q) ||
      p.codigoBarras?.includes(q) ||
      categorias.find(c => c.id === p.categoriaId)?.nombre.toLowerCase().includes(q)
    )
  );
}

// ---------- CREAR ----------
formCrear.addEventListener("submit", async e => {
  e.preventDefault();

  const fd = new FormData();

  const nuevoProducto: Producto = {
    nombre: formCrear.nombre.value,
    precioCosto: Number(formCrear.precioCosto.value),
    porcentajeGanancia: Number(formCrear.porcentajeGanancia.value),
    precioVenta: Number(formCrear.precioVenta.value),
    stock: Number(formCrear.stock.value),
    tipoCantidad: formCrear.tipoCantidad.value,
    unidadMedida: formCrear.unidadMedida.value,
    fraccionMinima: Number(formCrear.fraccionMinima.value),
    unidadesPorPaquete: formCrear.unidadesPorPaquete.value
      ? Number(formCrear.unidadesPorPaquete.value)
      : undefined,
    codigoBarras: formCrear.codigoBarras.value || undefined,
    proveedor: formCrear.proveedor.value || undefined,
    categoriaId: Number(formCrear.categoriaId.value)
  };

  // 🔹 JSON como string en FormData
  fd.append("producto", JSON.stringify(nuevoProducto));

  // 🔹 Imagen opcional
  const inputImagen = formCrear.querySelector("input[name='imagen']") as HTMLInputElement;
  if (inputImagen?.files?.[0]) {
    fd.append("imagen", inputImagen.files[0]);
  }

  try {
    await createProducto(fd);
    formCrear.reset();
    textoGananciaCrear.textContent = "30";
    (formCrear.tipoCantidad as HTMLSelectElement).value = "UNIDAD";
    (formCrear.unidadMedida as HTMLSelectElement).value = "u";
    (formCrear.fraccionMinima as HTMLInputElement).value = "1";
    (formCrear.tipoCantidad as HTMLSelectElement).dispatchEvent(new Event("change"));
    cargarProductos();
    mostrarToast("Producto creado correctamente");
    // El modal NO se cierra solo a propósito: para cargar varios productos
    // seguidos no hace falta reabrirlo cada vez, solo tocar "Cerrar" al
    // terminar. Refocar el primer campo para poder tipear el siguiente ya.
    (formCrear.nombre as HTMLInputElement).focus();
  } catch (err) {
    console.error(err);
    mostrarToast("Error al crear producto", true);
  }
});

// ---------- ABRIR EDITAR ----------
function abrirEditarProducto(id: number) {
  productoActual = productos.find(p => p.id === id) || null;
  if (!productoActual) return;

  const costo = productoActual.precioCosto || 0;
  const porcentaje =
    productoActual.porcentajeGanancia !== undefined
      ? productoActual.porcentajeGanancia
      : 30;

  formEditar.nombre.value = productoActual.nombre;
  formEditar.precioCosto.value = costo.toString();
  inputPorcentajeEditar.value = porcentaje.toString();
  textoGananciaEditar.textContent = porcentaje.toString();

  formEditar.precioVenta.value = calcularPrecioVenta(
    costo,
    porcentaje
  ).toString();

  formEditar.stock.value = productoActual.stock.toString();
  formEditar.tipoCantidad.value = productoActual.tipoCantidad || "UNIDAD";
  formEditar.unidadMedida.value = obtenerUnidad(productoActual);
  formEditar.fraccionMinima.value = obtenerFraccionMinima(productoActual).toString();
  formEditar.tipoCantidad.dispatchEvent(new Event("change"));
  formEditar.unidadesPorPaquete.value = productoActual.unidadesPorPaquete?.toString() || "";
  formEditar.codigoBarras.value = productoActual.codigoBarras || "";
  formEditar.proveedor.value = productoActual.proveedor || "";
  formEditar.categoriaId.value =
    productoActual.categoriaId?.toString() || "";

  modal.classList.remove("hidden");
}

// ---------- CANCELAR ----------
btnCancelar.addEventListener("click", () => {
  modal.classList.add("hidden");
  productoActual = null;
  formEditar.reset();
});

// ---------- MODAL CREAR ----------
btnCrearProducto.addEventListener("click", () => {
  modalCrear.classList.remove("hidden");
  (formCrear.nombre as HTMLInputElement).focus();
});

btnCerrarCrear.addEventListener("click", () => {
  modalCrear.classList.add("hidden");
});

// ---------- GUARDAR EDICIÓN ----------
formEditar.addEventListener("submit", async e => {
  e.preventDefault();
  if (!productoActual) return;

  const fd = new FormData();
  
  const productoEditado: Producto = {
    id: productoActual.id,
    nombre: formEditar.nombre.value,
    precioCosto: Number(formEditar.precioCosto.value),
    porcentajeGanancia: Number(formEditar.porcentajeGanancia.value),
    precioVenta: Number(formEditar.precioVenta.value),
    stock: Number(formEditar.stock.value),
    tipoCantidad: formEditar.tipoCantidad.value,
    unidadMedida: formEditar.unidadMedida.value,
    fraccionMinima: Number(formEditar.fraccionMinima.value),
    unidadesPorPaquete: formEditar.unidadesPorPaquete.value
      ? Number(formEditar.unidadesPorPaquete.value)
      : undefined,
    codigoBarras: formEditar.codigoBarras.value || undefined,
    proveedor: formEditar.proveedor.value || undefined,
    categoriaId: Number(formEditar.categoriaId.value)
  };

  // 🔹 JSON en el key "producto"
  fd.append("producto", JSON.stringify(productoEditado));

  // 🔹 Imagen opcional
  const inputImagen = formEditar.querySelector("input[name='imagen']") as HTMLInputElement;
  if (inputImagen?.files?.[0]) {
    fd.append("imagen", inputImagen.files[0]);
  }

  try {
    await updateProducto(productoActual.id!, fd);
    modal.classList.add("hidden");
    productoActual = null;
    cargarProductos();
    mostrarToast("Producto actualizado correctamente");
  } catch (err) {
    console.error(err);
    mostrarToast("Error al actualizar producto", true);
  }
});

// ---------- ELIMINAR ----------
async function eliminarProducto(id: number) {
  if (!(await confirmar("¿Eliminar producto?"))) return;
  await deleteProducto(id);
  seleccionados.delete(id);
  actualizarBarraSeleccion();
  cargarProductos();
  mostrarToast("Producto eliminado");
}

// ---------- BUSCAR ----------
inputBuscar.addEventListener("input", aplicarFiltro);

// ---------- RECALCULAR ----------
function recalcularVenta(
  inputCosto: HTMLInputElement,
  inputPorcentaje: HTMLInputElement,
  inputVenta: HTMLInputElement,
  textoGanancia?: HTMLSpanElement
) {
  const costo = Number(inputCosto.value);
  const porcentaje = Number(inputPorcentaje.value);

  if (!costo || porcentaje < 0) return;

  inputVenta.value = calcularPrecioVenta(costo, porcentaje).toString();

  if (textoGanancia) {
    textoGanancia.textContent = porcentaje.toString();
  }
}


// ---------- INIT ----------
cargarCategorias();
cargarProductos();
sincronizarTipoYUnidad(formCrear);
sincronizarTipoYUnidad(formEditar);

import { habilitarCierreConEscape } from "../../utils/modales";
import { esc } from "../../utils/format";
import { checkAdmin } from "../../services/auth.service";
checkAdmin();

import type { Promocion } from "../../models/Promocion";
import type { Producto } from "../../models/Producto";
import type { Categoria } from "../../models/Categoria";
import type { Descuento } from "../../models/Descuento";
import {
  getPromociones,
  createPromocion,
  updatePromocion,
  deletePromocion
} from "../../services/promociones.service";
import {
  getDescuentos,
  createDescuento,
  updateDescuento,
  deleteDescuento
} from "../../services/descuentos.service";
import { getProductos } from "../../services/productos.service";
import { getCategorias } from "../../services/categorias.service";
import { confirmar } from "../../services/confirm.service";
import { mostrarToast } from "../../utils/toast";

// ---------------- ESTADO ----------------
let promociones: Promocion[] = [];
let productos: Producto[] = [];
let categorias: Categoria[] = [];
let promocionActual: Promocion | null = null;
let descuentos: Descuento[] = [];
let descuentoActual: Descuento | null = null;

// ---------------- DOM ----------------
const tbody = document.querySelector("#tabla-promos tbody") as HTMLTableSectionElement;
const formCrear = document.getElementById("form-crear-promo") as HTMLFormElement;
const formEditar = document.getElementById("form-editar-promo") as HTMLFormElement;
const modal = document.getElementById("modal-editar") as HTMLDivElement;
const btnCancelar = document.getElementById("btn-cancelar") as HTMLButtonElement;
const editarProductoNombre = document.getElementById("editar-producto-nombre") as HTMLSpanElement;

const inputBuscarProducto = document.getElementById("input-buscar-producto") as HTMLInputElement;
const dropdownProductos = document.getElementById("dropdown-productos") as HTMLDivElement;

// ---------------- BUSCADOR DE PRODUCTOS (solo productos por UNIDAD) ----------------
function renderDropdownProductos(filtro: string) {
  const texto = filtro.toLowerCase().trim();
  if (!texto) {
    dropdownProductos.classList.add("hidden");
    dropdownProductos.innerHTML = "";
    return;
  }

  const coincidencias = productos
    .filter(p => p.tipoCantidad === "UNIDAD")
    .filter(p => p.nombre.toLowerCase().includes(texto))
    .slice(0, 50);

  dropdownProductos.innerHTML = "";

  if (coincidencias.length === 0) {
    dropdownProductos.innerHTML = `<div class="dropdown-sin-resultados">No se encontraron productos por unidad</div>`;
  } else {
    coincidencias.forEach(p => {
      const item = document.createElement("div");
      item.className = "dropdown-item";
      item.textContent = p.nombre;
      item.addEventListener("click", () => seleccionarProducto(p));
      dropdownProductos.appendChild(item);
    });
  }

  dropdownProductos.classList.remove("hidden");
}

function seleccionarProducto(producto: Producto) {
  inputBuscarProducto.value = producto.nombre;
  inputBuscarProducto.dataset.id = producto.id!.toString();
  dropdownProductos.classList.add("hidden");
  dropdownProductos.innerHTML = "";
}

inputBuscarProducto.addEventListener("input", () => {
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

// ---------------- CARGA ----------------
async function cargarProductos() {
  try {
    productos = await getProductos();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al cargar productos", true);
  }
}

async function cargarPromociones() {
  try {
    promociones = await getPromociones();
    renderPromociones();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al cargar promociones", true);
  }
}

// ---------------- RENDER ----------------
function renderPromociones() {
  tbody.innerHTML = "";

  promociones.forEach(promo => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <!-- DF-SEC-001: productoNombre lo escribe quien carga el producto. -->
      <td>${esc(promo.productoNombre) || `Producto ${promo.productoId}`}</td>
      <td>${promo.cantidadLleva}x${promo.cantidadPaga}</td>
      <td><span class="badge-estado ${promo.activo ? "badge-estado--activo" : "badge-estado--inactivo"}">${promo.activo ? "Activo" : "Inactivo"}</span></td>
      <td>
        <button class="editar" data-id="${promo.id}">Editar</button>
        <button class="eliminar" data-id="${promo.id}">Eliminar</button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  agregarEventos();
}

function agregarEventos() {
  document.querySelectorAll(".editar").forEach(btn => {
    btn.addEventListener("click", () =>
      abrirEditar(Number((btn as HTMLElement).dataset.id))
    );
  });

  document.querySelectorAll(".eliminar").forEach(btn => {
    btn.addEventListener("click", () =>
      eliminarPromocion(Number((btn as HTMLElement).dataset.id))
    );
  });
}

// ---------------- CREAR ----------------
formCrear.addEventListener("submit", async e => {
  e.preventDefault();

  const productoId = Number(inputBuscarProducto.dataset.id);
  const fd = new FormData(formCrear);
  const cantidadLleva = Number(fd.get("cantidadLleva"));
  const cantidadPaga = Number(fd.get("cantidadPaga"));
  const activo = fd.get("activo") === "on";

  if (!productoId) {
    mostrarToast("Elegí un producto de la lista", true);
    return;
  }

  if (cantidadPaga >= cantidadLleva) {
    mostrarToast("La cantidad a pagar debe ser menor a la cantidad que se lleva", true);
    return;
  }

  try {
    await createPromocion({ productoId, cantidadLleva, cantidadPaga, activo });
    mostrarToast("Promoción creada correctamente");
    formCrear.reset();
    inputBuscarProducto.value = "";
    delete inputBuscarProducto.dataset.id;
    cargarPromociones();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al crear promoción", true);
  }
});

// ---------------- EDITAR ----------------
function abrirEditar(id: number) {
  promocionActual = promociones.find(p => p.id === id) || null;
  if (!promocionActual) return;

  editarProductoNombre.textContent = promocionActual.productoNombre ?? `Producto ${promocionActual.productoId}`;
  formEditar.cantidadLleva.value = promocionActual.cantidadLleva;
  formEditar.cantidadPaga.value = promocionActual.cantidadPaga;
  formEditar.activo.checked = promocionActual.activo;

  modal.classList.remove("hidden");
}

formEditar.addEventListener("submit", async e => {
  e.preventDefault();
  if (!promocionActual) return;

  const fd = new FormData(formEditar);
  const cantidadLleva = Number(fd.get("cantidadLleva"));
  const cantidadPaga = Number(fd.get("cantidadPaga"));
  const activo = fd.get("activo") === "on";

  if (cantidadPaga >= cantidadLleva) {
    mostrarToast("La cantidad a pagar debe ser menor a la cantidad que se lleva", true);
    return;
  }

  try {
    await updatePromocion(promocionActual.id!, {
      id: promocionActual.id,
      productoId: promocionActual.productoId,
      cantidadLleva,
      cantidadPaga,
      activo
    });
    mostrarToast("Promoción actualizada correctamente");
    modal.classList.add("hidden");
    promocionActual = null;
    cargarPromociones();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al actualizar promoción", true);
  }
});

btnCancelar.addEventListener("click", () => {
  modal.classList.add("hidden");
  promocionActual = null;
});

// ---------------- ELIMINAR ----------------
async function eliminarPromocion(id: number) {
  if (!(await confirmar("¿Eliminar esta promoción?"))) return;
  try {
    await deletePromocion(id);
    mostrarToast("Promoción eliminada");
    cargarPromociones();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al eliminar promoción", true);
  }
}

// =================================================================
// DESCUENTOS POR % (producto o categoría entera)
// =================================================================

const tbodyDescuentos = document.querySelector("#tabla-descuentos tbody") as HTMLTableSectionElement;
const formCrearDescuento = document.getElementById("form-crear-descuento") as HTMLFormElement;
const formEditarDescuento = document.getElementById("form-editar-descuento") as HTMLFormElement;
const modalEditarDescuento = document.getElementById("modal-editar-descuento") as HTMLDivElement;
const btnCancelarDescuento = document.getElementById("btn-cancelar-descuento") as HTMLButtonElement;
const editarDescuentoAlcance = document.getElementById("editar-descuento-alcance") as HTMLSpanElement;

const selectAlcanceDescuento = document.getElementById("select-alcance-descuento") as HTMLSelectElement;
const labelProductoDescuento = document.getElementById("label-producto-descuento") as HTMLLabelElement;
const labelCategoriaDescuento = document.getElementById("label-categoria-descuento") as HTMLLabelElement;
const selectCategoriaDescuento = document.getElementById("select-categoria-descuento") as HTMLSelectElement;
const inputBuscarProductoDescuento = document.getElementById("input-buscar-producto-descuento") as HTMLInputElement;
const dropdownProductosDescuento = document.getElementById("dropdown-productos-descuento") as HTMLDivElement;

selectAlcanceDescuento.addEventListener("change", () => {
  const esProducto = selectAlcanceDescuento.value === "PRODUCTO";
  labelProductoDescuento.classList.toggle("hidden", !esProducto);
  labelCategoriaDescuento.classList.toggle("hidden", esProducto);
});

async function cargarCategorias() {
  try {
    categorias = await getCategorias();
    selectCategoriaDescuento.innerHTML = "";
    categorias.forEach(c => selectCategoriaDescuento.add(new Option(c.nombre, c.id.toString())));
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al cargar categorías", true);
  }
}

// A diferencia del buscador de Promociones (solo UNIDAD, por el combo de
// unidades), un descuento por % aplica a cualquier tipoCantidad.
function renderDropdownProductosDescuento(filtro: string) {
  const texto = filtro.toLowerCase().trim();
  if (!texto) {
    dropdownProductosDescuento.classList.add("hidden");
    dropdownProductosDescuento.innerHTML = "";
    return;
  }

  const coincidencias = productos
    .filter(p => p.nombre.toLowerCase().includes(texto))
    .slice(0, 50);

  dropdownProductosDescuento.innerHTML = "";

  if (coincidencias.length === 0) {
    dropdownProductosDescuento.innerHTML = `<div class="dropdown-sin-resultados">No se encontraron productos</div>`;
  } else {
    coincidencias.forEach(p => {
      const item = document.createElement("div");
      item.className = "dropdown-item";
      item.textContent = p.nombre;
      item.addEventListener("click", () => {
        inputBuscarProductoDescuento.value = p.nombre;
        inputBuscarProductoDescuento.dataset.id = p.id!.toString();
        dropdownProductosDescuento.classList.add("hidden");
        dropdownProductosDescuento.innerHTML = "";
      });
      dropdownProductosDescuento.appendChild(item);
    });
  }

  dropdownProductosDescuento.classList.remove("hidden");
}

inputBuscarProductoDescuento.addEventListener("input", () => {
  delete inputBuscarProductoDescuento.dataset.id;
  renderDropdownProductosDescuento(inputBuscarProductoDescuento.value);
});

inputBuscarProductoDescuento.addEventListener("focus", () => {
  if (inputBuscarProductoDescuento.value.trim()) renderDropdownProductosDescuento(inputBuscarProductoDescuento.value);
});

document.addEventListener("click", (e) => {
  const target = e.target as HTMLElement;
  if (!target.closest("#label-producto-descuento .producto-buscar-container")) {
    dropdownProductosDescuento.classList.add("hidden");
  }
});

async function cargarDescuentos() {
  try {
    descuentos = await getDescuentos();
    renderDescuentos();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al cargar descuentos", true);
  }
}

// DF-SEC-001: productoNombre/categoriaNombre los escribe quien carga el
// producto o la categoria, y esta etiqueta termina en innerHTML.
function etiquetaAlcance(d: Descuento): string {
  return d.alcance === "PRODUCTO"
    ? (esc(d.productoNombre) || `Producto ${d.productoId}`)
    : `Categoría: ${esc(d.categoriaNombre) || d.categoriaId}`;
}

function renderDescuentos() {
  tbodyDescuentos.innerHTML = "";

  descuentos.forEach(d => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${etiquetaAlcance(d)}</td>
      <td>${d.porcentaje}%</td>
      <td><span class="badge-estado ${d.activo ? "badge-estado--activo" : "badge-estado--inactivo"}">${d.activo ? "Activo" : "Inactivo"}</span></td>
      <td>
        <button class="editar-descuento" data-id="${d.id}">Editar</button>
        <button class="eliminar-descuento" data-id="${d.id}">Eliminar</button>
      </td>
    `;
    tbodyDescuentos.appendChild(tr);
  });

  document.querySelectorAll(".editar-descuento").forEach(btn => {
    btn.addEventListener("click", () => abrirEditarDescuento(Number((btn as HTMLElement).dataset.id)));
  });
  document.querySelectorAll(".eliminar-descuento").forEach(btn => {
    btn.addEventListener("click", () => eliminarDescuento(Number((btn as HTMLElement).dataset.id)));
  });
}

formCrearDescuento.addEventListener("submit", async e => {
  e.preventDefault();

  const alcance = selectAlcanceDescuento.value as "PRODUCTO" | "CATEGORIA";
  const fd = new FormData(formCrearDescuento);
  const porcentaje = Number(fd.get("porcentaje"));
  const activo = fd.get("activo") === "on";

  const dto: Omit<Descuento, "id"> = { alcance, porcentaje, activo };

  if (alcance === "PRODUCTO") {
    const productoId = Number(inputBuscarProductoDescuento.dataset.id);
    if (!productoId) {
      mostrarToast("Elegí un producto de la lista", true);
      return;
    }
    dto.productoId = productoId;
  } else {
    dto.categoriaId = Number(selectCategoriaDescuento.value);
  }

  try {
    await createDescuento(dto);
    mostrarToast("Descuento creado correctamente");
    formCrearDescuento.reset();
    inputBuscarProductoDescuento.value = "";
    delete inputBuscarProductoDescuento.dataset.id;
    labelProductoDescuento.classList.remove("hidden");
    labelCategoriaDescuento.classList.add("hidden");
    cargarDescuentos();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al crear descuento", true);
  }
});

function abrirEditarDescuento(id: number) {
  descuentoActual = descuentos.find(d => d.id === id) || null;
  if (!descuentoActual) return;

  editarDescuentoAlcance.textContent = etiquetaAlcance(descuentoActual);
  formEditarDescuento.porcentaje.value = descuentoActual.porcentaje;
  formEditarDescuento.activo.checked = descuentoActual.activo;

  modalEditarDescuento.classList.remove("hidden");
}

formEditarDescuento.addEventListener("submit", async e => {
  e.preventDefault();
  if (!descuentoActual) return;

  const fd = new FormData(formEditarDescuento);
  const porcentaje = Number(fd.get("porcentaje"));
  const activo = fd.get("activo") === "on";

  try {
    await updateDescuento(descuentoActual.id!, { ...descuentoActual, porcentaje, activo });
    mostrarToast("Descuento actualizado correctamente");
    modalEditarDescuento.classList.add("hidden");
    descuentoActual = null;
    cargarDescuentos();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al actualizar descuento", true);
  }
});

btnCancelarDescuento.addEventListener("click", () => {
  modalEditarDescuento.classList.add("hidden");
  descuentoActual = null;
});

async function eliminarDescuento(id: number) {
  if (!(await confirmar("¿Eliminar este descuento?"))) return;
  try {
    await deleteDescuento(id);
    mostrarToast("Descuento eliminado");
    cargarDescuentos();
  } catch (error) {
    console.error(error);
    mostrarToast((error as Error).message || "Error al eliminar descuento", true);
  }
}

// ---------------- INIT ----------------
cargarProductos();
cargarPromociones();
cargarCategorias();
cargarDescuentos();

// Escape cierra el modal visible en esta pantalla (misma logica en todas las pantallas con modal).
habilitarCierreConEscape();

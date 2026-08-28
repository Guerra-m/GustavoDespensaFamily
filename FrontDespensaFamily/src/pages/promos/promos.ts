import { checkAdmin } from "../../services/auth.service";
checkAdmin();

import type { Promocion } from "../../models/Promocion";
import type { Producto } from "../../models/Producto";
import {
  getPromociones,
  createPromocion,
  updatePromocion,
  deletePromocion
} from "../../services/promociones.service";
import { getProductos } from "../../services/productos.service";
import { confirmar } from "../../services/confirm.service";

// ---------------- ESTADO ----------------
let promociones: Promocion[] = [];
let productos: Producto[] = [];
let promocionActual: Promocion | null = null;

// ---------------- DOM ----------------
const tbody = document.querySelector("#tabla-promos tbody") as HTMLTableSectionElement;
const formCrear = document.getElementById("form-crear-promo") as HTMLFormElement;
const formEditar = document.getElementById("form-editar-promo") as HTMLFormElement;
const modal = document.getElementById("modal-editar") as HTMLDivElement;
const btnCancelar = document.getElementById("btn-cancelar") as HTMLButtonElement;
const editarProductoNombre = document.getElementById("editar-producto-nombre") as HTMLSpanElement;
const toast = document.getElementById("toast") as HTMLDivElement;

const inputBuscarProducto = document.getElementById("input-buscar-producto") as HTMLInputElement;
const dropdownProductos = document.getElementById("dropdown-productos") as HTMLDivElement;

// ---------------- TOAST ----------------
function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden", "error");
  if (error) toast.classList.add("error");
  setTimeout(() => toast.classList.add("hidden"), 3000);
}

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
    mostrarToast("Error al cargar productos", true);
  }
}

async function cargarPromociones() {
  try {
    promociones = await getPromociones();
    renderPromociones();
  } catch (error) {
    console.error(error);
    mostrarToast("Error al cargar promociones", true);
  }
}

// ---------------- RENDER ----------------
function renderPromociones() {
  tbody.innerHTML = "";

  promociones.forEach(promo => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${promo.productoNombre ?? `Producto ${promo.productoId}`}</td>
      <td>${promo.cantidadLleva}x${promo.cantidadPaga}</td>
      <td>${promo.activo ? "✅" : "—"}</td>
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
    mostrarToast("Error al eliminar promoción", true);
  }
}

// ---------------- INIT ----------------
cargarProductos();
cargarPromociones();

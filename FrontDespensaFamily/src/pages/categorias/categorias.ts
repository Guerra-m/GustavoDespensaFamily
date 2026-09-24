import { habilitarCierreConEscape } from "../../utils/modales";
import { esc } from "../../utils/format";
import { checkAuth, puedeGestionarProductos } from "../../services/auth.service";
checkAuth();

// Crear, editar o eliminar categorías es del dueño, o de un empleado con
// PERM_STOCK prendido (ver Usuario.java). El backend ya lo exige
// (SecurityConfig); esto solo evita mostrar controles que van a 403.
const PUEDE_GESTIONAR = puedeGestionarProductos();

import type { Categoria } from "../../models/Categoria";
import {
  getCategorias,
  createCategoria,
  updateCategoria,
  deleteCategoria
} from "../../services/categorias.service";
import { getProductos, updateProducto } from "../../services/productos.service";
import type { Producto } from "../../models/Producto";
import { confirmar } from "../../services/confirm.service";

// DOM
const grid = document.getElementById("categorias-grid") as HTMLDivElement;
const formCrear = document.getElementById("form-crear-categoria") as HTMLFormElement;
const formEditar = document.getElementById("form-editar-categoria") as HTMLFormElement;
const modal = document.getElementById("modal-editar") as HTMLDivElement;
const btnCancelar = document.getElementById("btn-cancelar") as HTMLButtonElement;

// Estado
let categorias: Categoria[] = [];
let productos: Producto[] = [];
let categoriaActual: Categoria | null = null;

function contarProductos(categoriaId: number): number {
  return productos.filter(p => p.categoriaId === categoriaId).length;
}

// Render
function renderCategorias() {
  grid.innerHTML = "";

  if (categorias.length === 0) {
    grid.innerHTML = `<div class="categorias-sin-resultados">No hay categorías creadas todavía</div>`;
    return;
  }

  categorias.forEach(cat => {
    const cantidad = contarProductos(cat.id!);
    const card = document.createElement("div");
    card.className = "categoria-card";
    card.innerHTML = `
      ${PUEDE_GESTIONAR ? `<div class="categoria-acciones">
        <button class="btn-editar-cat" data-id="${cat.id}" title="Editar"><span class="icon" style="--icono:url('/icons/pencil.svg')"></span></button>
        <button class="btn-eliminar-cat" data-id="${cat.id}" title="Eliminar"><span class="icon" style="--icono:url('/icons/trash-2.svg')"></span></button>
      </div>` : ""}
      <div class="categoria-nombre">${esc(cat.nombre)}</div>
      <!-- DF-SEC-001: reproducido en vivo con <img src=x onerror=...> en la descripcion. -->
      <div class="categoria-descripcion">${cat.descripcion ? esc(cat.descripcion) : "Sin descripción"}</div>
      <div class="categoria-contador">${cantidad} producto${cantidad === 1 ? "" : "s"}</div>
    `;
    grid.appendChild(card);
  });

  agregarEventos();
}

if (!PUEDE_GESTIONAR) {
  formCrear.classList.add("hidden");
}

// Cargar
async function cargarCategorias() {
  [categorias, productos] = await Promise.all([getCategorias(), getProductos()]);
  categorias.sort((a, b) => a.nombre.localeCompare(b.nombre));
  renderCategorias();
}

// Crear
formCrear.addEventListener("submit", async e => {
  e.preventDefault();

  const fd = new FormData(formCrear);
  const nueva = {
  nombre: fd.get("nombre") as string,
  descripcion: fd.get("descripcion") as string || undefined
};


  await createCategoria(nueva);
  formCrear.reset();
  cargarCategorias();
});

// Abrir editar
function abrirEditar(id: number) {
  categoriaActual = categorias.find(c => c.id === id) || null;
  if (!categoriaActual) return;

  formEditar.nombre.value = categoriaActual.nombre;
  formEditar.descripcion.value = categoriaActual.descripcion || "";

  modal.classList.remove("hidden");
}

// Guardar editar
formEditar.addEventListener("submit", async e => {
  e.preventDefault();
  if (!categoriaActual) return;

  const fd = new FormData(formEditar);

  const porcentaje = fd.get("porcentajeGanancia");

  const actualizada: Categoria = {
    id: categoriaActual.id,
    nombre: fd.get("nombre") as string,
    descripcion: fd.get("descripcion") as string || undefined
  };

  await updateCategoria(categoriaActual.id!, actualizada);

  if (porcentaje !== null && porcentaje !== "") {
    await actualizarGananciaProductos(
      categoriaActual.id!,
      Number(porcentaje)
    );
  }

  modal.classList.add("hidden");
  categoriaActual = null;
  cargarCategorias();
});


// Eliminar
async function eliminarCategoria(id: number) {
  if (!(await confirmar("¿Eliminar categoría?"))) return;
  await deleteCategoria(id);
  cargarCategorias();
}
async function actualizarGananciaProductos(
  categoriaId: number,
  porcentajeGanancia: number
) {
  const productosVigentes = await getProductos();

  const productosCategoria = productosVigentes.filter(
    p => p.categoriaId === categoriaId
  );

  for (const producto of productosCategoria) {
    const precioVentaNuevo =
  producto.precioCosto +
  (producto.precioCosto * porcentajeGanancia) / 100;

const productoActualizado = {
  ...producto,
  porcentajeGanancia,
  precioVenta: +precioVentaNuevo.toFixed(2)
};


    const fd = new FormData();
    fd.append("producto", JSON.stringify(productoActualizado));

    // ❗ NO mandamos imagen → backend la deja igual
    await updateProducto(producto.id!, fd);
  }
}


// Eventos de las cards
function agregarEventos() {
  document.querySelectorAll(".btn-editar-cat").forEach(btn => {
    btn.addEventListener("click", () =>
      abrirEditar(Number((btn as HTMLElement).dataset.id))
    );
  });

  document.querySelectorAll(".btn-eliminar-cat").forEach(btn => {
    btn.addEventListener("click", () =>
      eliminarCategoria(Number((btn as HTMLElement).dataset.id))
    );
  });
}

// Cancelar modal
btnCancelar.addEventListener("click", () => {
  modal.classList.add("hidden");
  categoriaActual = null;
});

// Init
cargarCategorias();

// Escape cierra el modal visible en esta pantalla (misma logica en todas las pantallas con modal).
habilitarCierreConEscape();

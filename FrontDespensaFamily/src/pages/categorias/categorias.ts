import { checkAuth } from "../../services/auth.service";
checkAuth();

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
const tbody = document.querySelector("#tabla-categorias tbody") as HTMLTableSectionElement;
const formCrear = document.getElementById("form-crear-categoria") as HTMLFormElement;
const formEditar = document.getElementById("form-editar-categoria") as HTMLFormElement;
const modal = document.getElementById("modal-editar") as HTMLDivElement;
const btnCancelar = document.getElementById("btn-cancelar") as HTMLButtonElement;

// Estado
let categorias: Categoria[] = [];
let categoriaActual: Categoria | null = null;

// Render
function renderCategorias() {
  tbody.innerHTML = "";

  categorias.forEach(cat => {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${cat.id}</td>
      <td>${cat.nombre}</td>
      <td>${cat.descripcion || ""}</td>
      <td>
        <button class="editar" data-id="${cat.id}">Editar</button>
        <button class="eliminar" data-id="${cat.id}">Eliminar</button>
      </td>
    `;
    tbody.appendChild(tr);
  });

  agregarEventos();
}

// Cargar
async function cargarCategorias() {
  categorias = await getCategorias();
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
  const productos = await getProductos();

  const productosCategoria = productos.filter(
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


// Eventos tabla
function agregarEventos() {
  document.querySelectorAll(".editar").forEach(btn => {
    btn.addEventListener("click", () =>
      abrirEditar(Number((btn as HTMLElement).dataset.id))
    );
  });

  document.querySelectorAll(".eliminar").forEach(btn => {
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

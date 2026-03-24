import type { Compra } from "../../models/Compra";
import type { CompraRequest, DetalleCompraRequest } from "../../models/CompraRequest";
import type { Producto } from "../../models/Producto";

import {
  getCompras,
  createCompra,
  getCompraDetalle
} from "../../services/compras.service";

import { getProductos } from "../../services/productos.service";

// ---------------- ESTADO ----------------
let compras: Compra[] = [];
let productos: Producto[] = [];
let detallesCompra: DetalleCompraRequest[] = [];

// ---------------- DOM ----------------
const tablaCompras = document.getElementById("tabla-compras") as HTMLTableElement;
const formCompra = document.getElementById("form-compra") as HTMLFormElement;

const inputProveedor = document.getElementById("proveedor") as HTMLInputElement;
const selectProducto = document.getElementById("producto") as HTMLSelectElement;
const inputCantidad = document.getElementById("cantidad") as HTMLInputElement;
const inputPrecioCosto = document.getElementById("precioCosto") as HTMLInputElement;

const btnAgregar = document.getElementById("btn-agregar") as HTMLButtonElement;
const tablaDetalles = document.getElementById("tabla-detalles") as HTMLTableElement;

// ---------------- FUNCIONES ----------------

// Cargar compras
async function cargarCompras() {
  try {
    compras = await getCompras();
    const comprasOrdenadas = [...compras].reverse();
    renderCompras(comprasOrdenadas);
  } catch (error) {
    console.error(error);
    alert("Error al cargar compras");
  }
}

// Render compras
function renderCompras(lista: Compra[]) {
    const tbody = document.querySelector("#tabla-compras tbody")!;
    tbody.innerHTML = "";

    lista.forEach(compra => {
        const tr = document.createElement("tr");
        tr.innerHTML = `
            <td>${compra.id}</td>
            <td>${compra.proveedor}</td>
            <td>${compra.fechaHora}</td>
        `;
        tbody.appendChild(tr);
        tr.addEventListener("click", () => {
        verDetalleCompra(compra.id!);
        });
    });

function agregarEventosDetalle() {
  const botones = document.querySelectorAll(".btn-detalle");

  botones.forEach(btn => {
    btn.addEventListener("click", () => {
      const id = Number((btn as HTMLElement).dataset.id);
      console.log("CLICK DETALLE", id); // 👈 debug
      verDetalleCompra(id);
    });
  });
}
// Agregar eventos a los botones después de renderizar
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
    selectProducto.innerHTML = "<option value=''>Seleccione producto</option>";

    productos.forEach(p => {
      const option = document.createElement("option");
      option.value = p.id!.toString();
      option.textContent = p.nombre;
      selectProducto.appendChild(option);
    });
  } catch (error) {
    console.error(error);
    alert("Error al cargar productos");
  }
}

// Agregar detalle
btnAgregar.addEventListener("click", () => {
  const productoId = Number(selectProducto.value);
  const cantidad = Number(inputCantidad.value);
  const precioCosto = Number(inputPrecioCosto.value);

  if (!productoId || cantidad <= 0 || precioCosto < 0) {
    alert("Datos inválidos");
    return;
  }

  detallesCompra.push({ productoId, cantidad, precioCosto });
  renderDetalles();

  inputCantidad.value = "";
  inputPrecioCosto.value = "";
});

// Render detalles
function renderDetalles() {
  const tbody = tablaDetalles.querySelector("tbody")!;
  tbody.innerHTML = "";

  detallesCompra.forEach((d, index) => {
    const producto = productos.find(p => p.id === d.productoId);

    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${producto?.nombre || "Producto"}</td>
      <td>${d.cantidad}</td>
      <td>${d.precioCosto}</td>
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
formCompra.addEventListener("submit", async e => {
  e.preventDefault();

  const proveedor = inputProveedor.value.trim();

  if (!proveedor) {
    alert("El proveedor es obligatorio");
    return;
  }

  if (detallesCompra.length === 0) {
    alert("Debe agregar al menos un producto");
    return;
  }

  const compra: CompraRequest = {
    proveedor,
    detalles: detallesCompra
  };

  try {
    await createCompra(compra);
    alert("Compra registrada correctamente");

    detallesCompra = [];
    renderDetalles();
    formCompra.reset();
    cargarCompras();
  } catch (error) {
    console.error(error);
    alert("Error al registrar compra");
  }
});

// ---------------- INIT ----------------
cargarCompras();
cargarProductos();
// ---------------- DOM modal ----------------
const modalDetalleCompra = document.getElementById("modal-detalle-compra") as HTMLDivElement;
const detalleCompraId = document.getElementById("detalle-compra-id") as HTMLSpanElement;
const detalleCompraProveedor = document.getElementById("detalle-compra-proveedor") as HTMLSpanElement;
const detalleCompraFecha = document.getElementById("detalle-compra-fecha") as HTMLSpanElement;
const tablaDetalleCompra = document.getElementById("tabla-detalle-compra") as HTMLTableSectionElement;
const detalleCompraTotal = document.getElementById("detalle-compra-total") as HTMLSpanElement;
const btnCerrarDetalleCompra = document.getElementById("btn-cerrar-detalle-compra") as HTMLButtonElement;

// ---------------- Ver detalle de compra (modal) ----------------
async function verDetalleCompra(id: number) {
    const compra = await getCompraDetalle(id);

    detalleCompraId.textContent = compra.id.toString();
    detalleCompraProveedor.textContent = compra.proveedor;
    detalleCompraFecha.textContent = compra.fechaHora;

    tablaDetalleCompra.innerHTML = "";
    let total = 0;

    compra.detalles.forEach(det => {
    const subtotal = det.cantidad * det.precioCosto;
    total += subtotal;

    const producto = productos.find(p => p.id === det.productoId);
    const nombre = producto ? producto.nombre : `Producto ${det.productoId}`;

    const tr = document.createElement("tr");
    tr.innerHTML = `
        <td>${nombre}</td>
        <td>${det.cantidad}</td>
        <td>$${det.precioCosto.toFixed(2)}</td>
        <td>$${subtotal.toFixed(2)}</td>
    `;
    tablaDetalleCompra.appendChild(tr);
});


    detalleCompraTotal.textContent = total.toFixed(2);

    // Mostrar modal
    modalDetalleCompra.classList.remove("hidden");
}
//------------- Cerrar modal ----------------
btnCerrarDetalleCompra.addEventListener("click", () => {
    modalDetalleCompra.classList.add("hidden");
});

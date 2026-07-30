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

// ---------- ELEMENTOS DEL DOM ----------
const productosGrid = document.getElementById("productos-grid") as HTMLDivElement;

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

// ---------- UTIL ----------
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

  const aplicarValores = () => {
    if (tipo.value === "PESO") {
      unidad.value = "kg";
      fraccion.value = fraccion.value && Number(fraccion.value) < 1 ? fraccion.value : "0.001";
      fraccion.step = "0.001";
      fraccion.min = "0.001";
      stock.step = "0.001";
      stock.min = "0.001";
    } else {
      unidad.value = "u";
      fraccion.value = "1";
      fraccion.step = "1";
      fraccion.min = "1";
      stock.step = "1";
      stock.min = "0";
    }
  };

  tipo.addEventListener("change", aplicarValores);
  aplicarValores();
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
    <div class="producto-menu-container">
      <button class="btn-menu" data-id="${prod.id}"></button>
      <div class="producto-menu-dropdown">
        <button class="btn-editar-menu" data-id="${prod.id}">Editar</button>
        <button class="btn-imprimir-menu" data-id="${prod.id}">Imprimir cartel</button>
        <button class="btn-eliminar-menu" data-id="${prod.id}">Eliminar</button>
      </div>
    </div>
    <div class="producto-imagen">
      ${prod.imagenUrl ? `<img src="${prod.imagenUrl}" alt="${prod.nombre}" loading="lazy">` : ''}
    </div>
    <div class="producto-info">
      <div class="producto-nombre">${prod.nombre}</div>
      <div class="producto-precio">$${prod.precioVenta.toFixed(2)} / ${obtenerUnidad(prod)}</div>
      <div class="producto-stock">Stock: ${prod.stock} ${obtenerUnidad(prod)}</div>
    </div>
  `;
  return card;
}

// ---------- IMPRIMIR CARTEL DE PRODUCTO ----------
function imprimirCartelProducto(prod: Producto) {
  const ventana = window.open("", "_blank");
  if (!ventana) return;

  const unidad = obtenerUnidad(prod);
  const precio = prod.precioVenta.toFixed(2);
  const categoria = categorias.find(c => c.id === prod.categoriaId)?.nombre ?? "";
  const ahora = new Date().toLocaleString("es-AR", {
    dateStyle: "long",
    timeStyle: "short"
  });

  ventana.document.write(`<!DOCTYPE html>
<html lang="es">
<head>
  <meta charset="UTF-8">
  <title>Cartel — ${prod.nombre}</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    html, body {
      height: 100%;
      font-family: 'Segoe UI', Arial, sans-serif;
      background: #fff;
      print-color-adjust: exact;
      -webkit-print-color-adjust: exact;
    }
    body {
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 24px;
    }
    .cartel {
      width: 100%;
      max-width: 480px;
      border: 3px solid #0288d1;
      border-radius: 20px;
      padding: 32px 28px;
      text-align: center;
    }
    .cartel-marca {
      font-size: 13px;
      font-weight: 700;
      letter-spacing: 1px;
      text-transform: uppercase;
      color: #0288d1;
      margin-bottom: 6px;
    }
    .cartel-categoria {
      font-size: 12px;
      color: #90a4ae;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      margin-bottom: 18px;
    }
    .cartel-nombre {
      font-size: 30px;
      font-weight: 800;
      color: #212529;
      line-height: 1.2;
      margin-bottom: 24px;
      word-break: break-word;
    }
    .cartel-precio {
      font-size: 72px;
      font-weight: 900;
      color: #0288d1;
      line-height: 1;
    }
    .cartel-precio span {
      font-size: 32px;
      vertical-align: super;
    }
    .cartel-unidad {
      font-size: 16px;
      font-weight: 600;
      color: #546e7a;
      margin-top: 8px;
      margin-bottom: 20px;
    }
    .cartel-codigo {
      font-size: 13px;
      letter-spacing: 2px;
      color: #78909c;
      border-top: 1px dashed #cfd8dc;
      padding-top: 12px;
      margin-top: 4px;
    }
    .cartel-fecha {
      font-size: 10px;
      color: #b0bec5;
      margin-top: 10px;
    }
    @media print {
      @page { margin: 15mm; size: A5 portrait; }
      body { padding: 0; }
      .cartel { border-width: 2px; }
    }
  </style>
</head>
<body>
  <div class="cartel">
    <div class="cartel-marca">🏪 DespensaFamily</div>
    ${categoria ? `<div class="cartel-categoria">${categoria}</div>` : ""}
    <div class="cartel-nombre">${prod.nombre}</div>
    <div class="cartel-precio"><span>$</span>${precio}</div>
    <div class="cartel-unidad">por ${unidad}</div>
    ${prod.codigoBarras ? `<div class="cartel-codigo">${prod.codigoBarras}</div>` : ""}
    <div class="cartel-fecha">Impreso el ${ahora}</div>
  </div>
</body>
</html>`);

  ventana.document.close();
  ventana.print();
}

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

  gruposOrdenados.forEach(([, { nombre, prods }]) => {
    const seccion = document.createElement("div");
    seccion.className = "prod-cat-seccion";

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
}

// ---------- DELEGACIÓN DE EVENTOS PARA EL MENÚ ----------
productosGrid.addEventListener("click", (e) => {
  const target = e.target as HTMLElement;

  // BOTÓN EDITAR
  if (target.closest('.btn-editar-menu')) {
    const btn = target.closest('.btn-editar-menu') as HTMLElement;
    const id = Number(btn.dataset.id);
    cerrarTodosLosMenus();
    abrirEditarProducto(id);
  }

  // BOTÓN IMPRIMIR CARTEL
  if (target.closest('.btn-imprimir-menu')) {
    const btn = target.closest('.btn-imprimir-menu') as HTMLElement;
    const id = Number(btn.dataset.id);
    cerrarTodosLosMenus();
    const prod = productos.find(p => p.id === id);
    if (prod) imprimirCartelProducto(prod);
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
  renderProductos(
    [...productos].sort((a, b) => a.nombre.localeCompare(b.nombre))
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
  } catch (err) {
    console.error(err);
    alert("Error al crear producto");
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
  } catch (err) {
    console.error(err);
    alert("Error al actualizar producto");
  }
});

// ---------- ELIMINAR ----------
async function eliminarProducto(id: number) {
  if (!confirm("¿Eliminar producto?")) return;
  await deleteProducto(id);
  cargarProductos();
}

// ---------- BUSCAR ----------
inputBuscar.addEventListener("input", () => {
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
});

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

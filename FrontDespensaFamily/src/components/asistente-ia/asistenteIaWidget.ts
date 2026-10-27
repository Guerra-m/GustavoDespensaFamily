import { esc } from "../../utils/format";
import type { ProductoExtraido } from "../../models/ProductoExtraido";
import type { Categoria } from "../../models/Categoria";
import { extraerProductos } from "../../services/asistenteIa.service";
import { createProducto } from "../../services/productos.service";

// Widget compartido del Asistente IA: la misma lógica (analizar -> preview
// editable -> confirmar de a uno) se usa tanto en la página propia
// (pages/asistente-ia) como en la pestaña "Con IA" del modal de Crear
// Producto. Se saca a un módulo común para no duplicar el flujo en dos
// lugares — nunca guarda nada directo, siempre pasa por el mismo
// POST /productos de siempre (ver AsistenteCargaProductosService del backend:
// la IA es un asistente, no la fuente de verdad).

export interface AsistenteIaWidgetRefs {
  textarea: HTMLTextAreaElement;
  inputImagen: HTMLInputElement;
  previewImagen: HTMLImageElement;
  btnAnalizar: HTMLButtonElement;
  seccionPreview: HTMLElement;
  tabla: HTMLTableElement;
  btnConfirmar: HTMLButtonElement;
  btnCancelar: HTMLButtonElement;
  mostrarToast: (mensaje: string, error?: boolean) => void;
}

interface FilaPreview {
  nombre: string;
  categoriaId: number | null;
  codigoBarras: string;
  precio: number | null;
  stock: number;
  unidad: "u" | "kg";
  confianza?: string;
  estado: "pendiente" | "creando" | "creado" | "error";
  mensajeError?: string;
}

export function inicializarAsistenteIaWidget(
  refs: AsistenteIaWidgetRefs,
  obtenerCategorias: () => Categoria[],
  alConfirmarTodo?: () => void
) {
  let filas: FilaPreview[] = [];
  let imagenSeleccionada: File | null = null;

  refs.inputImagen.addEventListener("change", () => {
    const archivo = refs.inputImagen.files?.[0] || null;
    imagenSeleccionada = archivo;
    if (archivo) {
      refs.previewImagen.src = URL.createObjectURL(archivo);
      refs.previewImagen.classList.remove("hidden");
    } else {
      refs.previewImagen.classList.add("hidden");
    }
  });

  function matchearCategoria(sugerida?: string): number | null {
    if (!sugerida) return null;
    const categorias = obtenerCategorias();
    const normalizada = sugerida.trim().toLowerCase();
    const encontrada = categorias.find(c => c.nombre.trim().toLowerCase() === normalizada)
      || categorias.find(c => c.nombre.trim().toLowerCase().includes(normalizada) || normalizada.includes(c.nombre.trim().toLowerCase()));
    return encontrada ? encontrada.id : null;
  }

  function mapearAFila(p: ProductoExtraido): FilaPreview {
    return {
      nombre: p.nombre || "",
      categoriaId: matchearCategoria(p.categoriaSugerida),
      codigoBarras: p.codigoBarras || "",
      precio: p.precio ?? null,
      stock: 0,
      unidad: p.unidad === "kg" ? "kg" : "u",
      confianza: p.confianza,
      estado: "pendiente"
    };
  }

  function filaCompleta(f: FilaPreview): boolean {
    return f.nombre.trim() !== "" && f.categoriaId !== null && f.precio !== null && f.precio > 0;
  }

  refs.btnAnalizar.addEventListener("click", async () => {
    const texto = refs.textarea.value.trim();
    if (!texto && !imagenSeleccionada) {
      refs.mostrarToast("Escribí una lista o subí una foto primero", true);
      return;
    }

    const textoOriginalBoton = refs.btnAnalizar.innerHTML;
    refs.btnAnalizar.disabled = true;
    refs.btnAnalizar.textContent = "Analizando...";

    try {
      const extraidos = await extraerProductos(texto, imagenSeleccionada);
      if (extraidos.length === 0) {
        refs.mostrarToast("No se identificó ningún producto. Probá con otro texto o foto.", true);
        return;
      }

      filas = extraidos.map(mapearAFila);
      renderPreview();
      refs.seccionPreview.classList.remove("hidden");
      refs.seccionPreview.scrollIntoView({ behavior: "smooth", block: "nearest" });
    } catch (error) {
      console.error(error);
      refs.mostrarToast(error instanceof Error ? error.message : "Error al analizar", true);
    } finally {
      refs.btnAnalizar.disabled = false;
      refs.btnAnalizar.innerHTML = textoOriginalBoton;
    }
  });

  function bloqueada(fila: FilaPreview): string {
    return fila.estado === "creado" || fila.estado === "creando" ? "disabled" : "";
  }

  function motivoIncompleta(fila: FilaPreview): string {
    if (!fila.nombre.trim()) return "Falta nombre";
    if (fila.categoriaId === null) return "Falta categoría";
    if (fila.precio === null || fila.precio <= 0) return "Falta precio";
    return "Incompleta";
  }

  function renderEstado(fila: FilaPreview, completa: boolean): string {
    if (fila.estado === "creado") return `<span class="estado-ok">✓ Creado</span>`;
    if (fila.estado === "creando") return `<span class="estado-pendiente">Guardando...</span>`;
    if (fila.estado === "error") return `<span class="estado-error">✗ ${fila.mensajeError || "Error"}</span>`;
    if (!completa) return `<span class="estado-advertencia">⚠ ${motivoIncompleta(fila)}</span>`;
    if (fila.confianza && fila.confianza !== "ALTA") return `<span class="estado-advertencia">⚠ Confianza ${fila.confianza.toLowerCase()}</span>`;
    return `<span class="estado-ok">✓ Lista</span>`;
  }

  function renderAccion(fila: FilaPreview, index: number): string {
    if (fila.estado === "creado") return "";
    return `<button type="button" class="btn-quitar-fila" data-index="${index}">Quitar</button>`;
  }

  function escapeHtml(texto: string): string {
    const div = document.createElement("div");
    div.textContent = texto;
    return div.innerHTML;
  }

  function renderPreview() {
    const tbody = refs.tabla.querySelector("tbody")!;
    tbody.innerHTML = "";
    const categorias = obtenerCategorias();

    filas.forEach((fila, index) => {
      const completa = filaCompleta(fila);
      const tr = document.createElement("tr");
      tr.className = fila.estado === "creado" ? "fila-creada" : fila.estado === "error" ? "fila-error" : "";

      const opcionesCategoria = categorias
        .map(c => `<option value="${c.id}" ${fila.categoriaId === c.id ? "selected" : ""}>${esc(c.nombre)}</option>`)
        .join("");

      tr.innerHTML = `
        <td><input type="text" class="celda-nombre" data-index="${index}" value="${escapeHtml(fila.nombre)}" ${bloqueada(fila)} /></td>
        <td>
          <select class="celda-categoria" data-index="${index}" ${bloqueada(fila)}>
            <option value="">Sin categoría</option>
            ${opcionesCategoria}
          </select>
        </td>
        <td><input type="text" class="celda-codigo" data-index="${index}" value="${escapeHtml(fila.codigoBarras)}" ${bloqueada(fila)} /></td>
        <td><input type="number" class="celda-precio" data-index="${index}" min="0" step="0.01" value="${fila.precio ?? ""}" placeholder="Falta precio" ${bloqueada(fila)} /></td>
        <td><input type="number" class="celda-stock" data-index="${index}" min="0" step="1" value="${fila.stock}" ${bloqueada(fila)} /></td>
        <td>${renderEstado(fila, completa)}</td>
        <td>${renderAccion(fila, index)}</td>
      `;
      tbody.appendChild(tr);
    });

    agregarEventosFilas();
  }

  function agregarEventosFilas() {
    refs.tabla.querySelectorAll<HTMLInputElement>(".celda-nombre").forEach(input => {
      input.addEventListener("input", () => {
        filas[Number(input.dataset.index)].nombre = input.value;
        renderPreview();
      });
    });

    refs.tabla.querySelectorAll<HTMLSelectElement>(".celda-categoria").forEach(select => {
      select.addEventListener("change", () => {
        const valor = select.value;
        filas[Number(select.dataset.index)].categoriaId = valor ? Number(valor) : null;
        renderPreview();
      });
    });

    refs.tabla.querySelectorAll<HTMLInputElement>(".celda-codigo").forEach(input => {
      input.addEventListener("input", () => {
        filas[Number(input.dataset.index)].codigoBarras = input.value;
      });
    });

    refs.tabla.querySelectorAll<HTMLInputElement>(".celda-precio").forEach(input => {
      input.addEventListener("input", () => {
        const valor = input.value === "" ? null : Number(input.value);
        filas[Number(input.dataset.index)].precio = valor;
        renderPreview();
      });
    });

    refs.tabla.querySelectorAll<HTMLInputElement>(".celda-stock").forEach(input => {
      input.addEventListener("input", () => {
        filas[Number(input.dataset.index)].stock = Number(input.value) || 0;
      });
    });

    refs.tabla.querySelectorAll<HTMLButtonElement>(".btn-quitar-fila").forEach(btn => {
      btn.addEventListener("click", () => {
        filas.splice(Number(btn.dataset.index), 1);
        renderPreview();
        if (filas.length === 0) refs.seccionPreview.classList.add("hidden");
      });
    });
  }

  refs.btnConfirmar.addEventListener("click", async () => {
    const pendientes = filas
      .map((f, i) => ({ f, i }))
      .filter(({ f }) => f.estado === "pendiente" && filaCompleta(f));

    if (pendientes.length === 0) {
      refs.mostrarToast("No hay filas completas para confirmar. Revisá las marcadas con ⚠.", true);
      return;
    }

    refs.btnConfirmar.disabled = true;
    let creados = 0;
    let fallidos = 0;

    for (const { f, i } of pendientes) {
      filas[i].estado = "creando";
      renderPreview();

      const fd = new FormData();
      fd.append("producto", JSON.stringify({
        nombre: f.nombre.trim(),
        precioCosto: f.precio,
        porcentajeGanancia: 0,
        precioVenta: f.precio,
        stock: f.stock,
        tipoCantidad: "UNIDAD",
        unidadMedida: f.unidad,
        fraccionMinima: 1,
        codigoBarras: f.codigoBarras.trim() || undefined,
        categoriaId: f.categoriaId
      }));

      try {
        await createProducto(fd);
        filas[i].estado = "creado";
        creados++;
      } catch (error) {
        console.error(error);
        filas[i].estado = "error";
        filas[i].mensajeError = "No se pudo crear";
        fallidos++;
      }
      renderPreview();
    }

    refs.btnConfirmar.disabled = false;
    refs.mostrarToast(
      fallidos === 0
        ? `${creados} producto${creados === 1 ? "" : "s"} creado${creados === 1 ? "" : "s"} correctamente`
        : `${creados} creado${creados === 1 ? "" : "s"}, ${fallidos} con error`,
      fallidos > 0
    );
    if (creados > 0) alConfirmarTodo?.();
  });

  refs.btnCancelar.addEventListener("click", () => {
    filas = [];
    refs.seccionPreview.classList.add("hidden");
    refs.textarea.value = "";
    refs.inputImagen.value = "";
    imagenSeleccionada = null;
    refs.previewImagen.classList.add("hidden");
  });
}

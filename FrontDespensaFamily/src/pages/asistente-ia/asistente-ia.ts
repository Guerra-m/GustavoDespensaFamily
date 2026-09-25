import { checkAuth } from "../../services/auth.service";
checkAuth();

import type { Categoria } from "../../models/Categoria";
import { getCategorias } from "../../services/categorias.service";
import { inicializarAsistenteIaWidget } from "../../components/asistente-ia/asistenteIaWidget";

let categorias: Categoria[] = [];

const toast = document.getElementById("toast") as HTMLDivElement;

function mostrarToast(mensaje: string, error = false) {
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  toast.classList.toggle("error", error);
  setTimeout(() => toast.classList.add("hidden"), 3500);
}

inicializarAsistenteIaWidget(
  {
    textarea: document.getElementById("input-texto") as HTMLTextAreaElement,
    inputImagen: document.getElementById("input-imagen") as HTMLInputElement,
    previewImagen: document.getElementById("preview-imagen") as HTMLImageElement,
    btnAnalizar: document.getElementById("btn-analizar") as HTMLButtonElement,
    seccionPreview: document.getElementById("seccion-preview") as HTMLDivElement,
    tabla: document.getElementById("tabla-preview") as HTMLTableElement,
    btnConfirmar: document.getElementById("btn-confirmar-todos") as HTMLButtonElement,
    btnCancelar: document.getElementById("btn-cancelar-preview") as HTMLButtonElement,
    mostrarToast
  },
  () => categorias
);

// ---------------- INIT ----------------
// DF-UX-008: sin catch, con el backend caído esto quedaba en el aire sin
// avisar — el widget de IA queda usable pero sin categorías para matchear.
getCategorias()
  .then(c => (categorias = c))
  .catch(err => {
    console.error(err);
    mostrarToast((err as Error).message || "No se pudieron cargar las categorías", true);
  });

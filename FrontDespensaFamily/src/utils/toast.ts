// utils/toast.ts
// mostrarToast() estaba copiada y pegada en las 12 páginas, cada una con su
// propio timeout (2000 a 3500 ms) y su propio <div id="toast"> tomado una
// sola vez al cargar el módulo. Fase 3, DF-DEBT-002: una sola función.
//
// Busca el elemento en cada llamada (no lo cachea a nivel de módulo) porque
// home.ts lo trataba como opcional (la página podía no tener #toast); acá
// el mismo guard sirve para cualquier página.
//
// Posición/color/animación: definidos UNA sola vez en style.css (.toast);
// las hojas de compras/promos/usuarios/ventas tenían una copia con
// `top: auto; bottom: 28px` que competía con el `top: 80px` de style.css
// (Fase 2 lo dejó documentado pero sin corregir). Se borraron esas copias.
const TIMEOUT_MS = 3000;

let ocultarTimeout: ReturnType<typeof setTimeout> | undefined;

export function mostrarToast(mensaje: string, error = false) {
  const toast = document.getElementById("toast") as HTMLDivElement | null;
  if (!toast) return;
  toast.textContent = mensaje;
  toast.classList.remove("hidden");
  toast.classList.toggle("error", error);
  clearTimeout(ocultarTimeout);
  ocultarTimeout = setTimeout(() => toast.classList.add("hidden"), TIMEOUT_MS);
}
